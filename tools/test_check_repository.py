"""Regression checks for the public-content guard; no real private data is used."""
import io
import hashlib
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
import zipfile

from check_repository import AUTHOR_EMAIL, WRAPPER_PATH, check_archive, check_source, issues_in_text


def archive(entries):
    result = io.BytesIO()
    with zipfile.ZipFile(result, "w") as output:
        for name, content in entries.items():
            output.writestr(name, content)
    return result.getvalue()


class PublicationChecks(unittest.TestCase):
    def test_registered_mark_and_public_identity_are_allowed(self):
        self.assertEqual(issues_in_text("README.md", "Lauterbach TRACE32®\n" + AUTHOR_EMAIL), [])
        self.assertEqual(issues_in_text("README.md", "`" + AUTHOR_EMAIL + "`"), [])

    def test_private_profile_path_is_rejected_in_tests_too(self):
        path = "C:" + "\\Users\\example-person\\project"
        for name in ("src/test/Example.java", "practice-core/src/test/Example.java", "intellij-plugin/src/test/Example.java"):
            self.assertIn("user-home-path", issues_in_text(name, path)[0])

    def test_unix_profile_path_is_rejected(self):
        path = "/" + "home/example-person/project"
        self.assertTrue(issues_in_text("README.md", path))

    def test_synthetic_platform_paths_are_allowed_only_in_tests(self):
        path = "C:" + "/project/script.cmm"
        for name in ("src/test/Example.java", "practice-core/src/test/Example.java", "intellij-plugin/src/test/Example.java"):
            self.assertEqual(issues_in_text(name, path), [])
        for name in ("README.md", "intellij-plugin/src/main/Example.kt", "arbitrary/src/test/Example.java"):
            self.assertTrue(issues_in_text(name, path))

    def test_only_the_exact_verified_wrapper_binary_is_allowed(self):
        content = b"synthetic wrapper fixture"
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            wrapper = root / WRAPPER_PATH
            wrapper.parent.mkdir(parents=True)
            wrapper.write_bytes(content)
            other = root / "other.jar"
            other.write_bytes(content)
            with patch("check_repository.candidate_files", return_value=[WRAPPER_PATH, "other.jar"]), patch("check_repository.WRAPPER_SHA256", hashlib.sha256(content).hexdigest()):
                problems, _ = check_source(root)
                self.assertEqual(problems, ["other.jar: binary-or-credential-file-in-source"])
                wrapper.write_bytes(content + b"changed")
                problems, _ = check_source(root)
                self.assertIn(WRAPPER_PATH + ": unverified-gradle-wrapper", problems)

    def test_nested_module_build_output_is_rejected_if_tracked(self):
        with tempfile.TemporaryDirectory() as directory:
            with patch("check_repository.candidate_files", return_value=["practice-core/build/report.txt"]):
                problems, _ = check_source(Path(directory))
                self.assertEqual(problems, ["practice-core/build/report.txt: generated-or-private-file"])

    def test_other_email_addresses_are_rejected_without_echoing_them(self):
        address = "someone" + "@" + "example.net"
        result = issues_in_text("README.md", address)
        self.assertTrue(result)
        self.assertNotIn(address, result[0])

    def test_secret_and_non_english_text_are_rejected(self):
        self.assertTrue(issues_in_text("README.md", "ghp_" + "a" * 40))
        self.assertTrue(issues_in_text("README.md", "\u4e2d\u6587"))

    def test_archive_rejects_traversal(self):
        self.assertTrue(check_archive(archive({"../escape": "x"}), "plugin.zip"))

    def test_nested_jar_is_audited(self):
        jar = archive({"META-INF/plugin.xml": "C:" + "\\Users\\example-person\\project"})
        self.assertTrue(check_archive(archive({"toolkit/lib/plugin.jar": jar}), "plugin.zip"))

    def test_clean_nested_distribution_is_accepted(self):
        jar = archive({"META-INF/plugin.xml": "Toolkit for Lauterbach TRACE32®"})
        self.assertEqual(check_archive(archive({"toolkit/lib/plugin.jar": jar}), "plugin.zip"), [])

    def test_test_classes_and_extra_runtime_are_rejected(self):
        for name in ("ExampleTest.class", "kotlin/Unit.class"):
            self.assertTrue(check_archive(archive({name: b"class data"}), "plugin.jar"))


if __name__ == "__main__":
    unittest.main()
