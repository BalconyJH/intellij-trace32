"""Regression checks for the public-content guard; no real private data is used."""
import io
import unittest
import zipfile

from check_repository import AUTHOR_EMAIL, check_archive, issues_in_text


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
        self.assertIn("user-home-path", issues_in_text("src/test/Example.java", path)[0])

    def test_unix_profile_path_is_rejected(self):
        path = "/" + "home/example-person/project"
        self.assertTrue(issues_in_text("README.md", path))

    def test_synthetic_platform_paths_are_allowed_only_in_tests(self):
        path = "C:" + "/project/script.cmm"
        self.assertEqual(issues_in_text("src/test/Example.java", path), [])
        self.assertTrue(issues_in_text("README.md", path))

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
