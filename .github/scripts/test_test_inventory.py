from pathlib import Path
import json,tempfile,unittest
from test_inventory import inventory,source_tests

class InventoryTest(unittest.TestCase):
    def test_multiple_classes_keep_their_own_methods(self):
        actual=source_tests('package p\nclass DesktopHoldKeysTest {@Test fun hold() {}}\nclass DesktopShortcutsTest {@Test fun shortcut() {}}')
        self.assertEqual({('p.DesktopHoldKeysTest','hold'),('p.DesktopShortcutsTest','shortcut')},actual)
    def test_filename_does_not_define_class(self):
        with tempfile.TemporaryDirectory() as folder:
            Path(folder,'Unrelated.kt').write_text('package p\nclass RealTest {@Test fun actual() {}}')
            self.assertEqual({('p.RealTest','actual')},inventory(folder))
    def test_comments_strings_and_raw_strings_are_not_tests(self):
        source='package p\n/* class Fake { /* nested */ @Test fun fake() {} } */\nclass RealTest {val text="class Nope {@Test fun wrong(){}}"\nval raw="""} @Test fun absent() {}"""\n@Test fun real() {val quote=\'}\'}}'
        self.assertEqual({('p.RealTest','real')},source_tests(source))
    def test_escaped_quotes_do_not_create_declarations(self):
        source='package p\nclass T {val text='+json.dumps('quote " class Fake { @Test fun fake() {} }')+'\n@Test fun real() {}}'
        self.assertEqual({('p.T','real')},source_tests(source))
    def test_nested_class_uses_jvm_binary_name(self):
        self.assertEqual({('p.Outer$Inner','nested')},source_tests('package p\nclass Outer {class Inner {@Test fun nested() {}}}'))
    def test_annotation_arguments_and_extra_annotations(self):
        actual=source_tests('package p\nclass RealTest {@org.junit.Test(expected=Failure::class) @Suppress("unused") fun works() {}}')
        self.assertEqual({('p.RealTest','works')},actual)
    def test_expression_bodies_and_backtick_names(self):
        source='package p\nclass First {@Test fun `hold remains`() = runBlocking {if(true) {}}}\nclass Second {@Test fun other() {}}'
        self.assertEqual({('p.First','hold remains'),('p.Second','other')},source_tests(source))
    def test_java_public_final_method(self):
        self.assertEqual({('p.JavaTest','works')},source_tests('package p; public class JavaTest {@Test public final void works() {}}'))
    def test_invalid_and_duplicate_declarations_fail_closed(self):
        for source in ['package p\n@Test fun orphan() {}','package p\nclass T {@Test fun same() {} @Test fun same() {}}']:
            with self.assertRaises(AssertionError): source_tests(source)

if __name__=='__main__': unittest.main()
