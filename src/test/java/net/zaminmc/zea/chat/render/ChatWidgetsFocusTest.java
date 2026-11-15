package net.zaminmc.zea.chat.render;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Pins the chat widgets out of 26.3's focus machinery on both of its
 * paths.
 *
 * <p><b>Keyboard</b> — vanilla screens move focus with Tab and the arrow
 * keys; with the tab strip, search bar and action buttons registered as
 * ordinary focusable children, a history-recall up-arrow while typing
 * moved screen focus onto them and silently ate every following
 * keystroke (the dev.24 live report). All widgets therefore override
 * {@code nextFocusPath} to return null.
 *
 * <p><b>Mouse</b> — 26.3's container click handler moves screen focus
 * onto whatever widget consumed a click ({@code
 * shouldTakeFocusAfterInteraction}, true by default), which clears the
 * chat input's focused flag: after one tab click, typed characters
 * reached no widget until the input was clicked again (the dev.36 live
 * report: "chat gets de-focused often"). Every mouse-driven widget
 * therefore overrides it to return false — the caret belongs to the
 * input through every click. The search bar is deliberately exempt:
 * it is a text field, and clicking into it should focus it.
 *
 * <p>Instantiating the widgets needs a real {@code Font} (they measure
 * their labels at construction), so this reads the compiled bytecode
 * instead — the same approach ChatSubsystemWiringTest takes — and
 * asserts the overrides exist and can only answer one way.
 */
class ChatWidgetsFocusTest {
        private static final Path MAIN_CLASSES = Paths.get("build", "classes", "java", "main");

        private static final String[] MOUSE_DRIVEN_WIDGETS = {
                        "net/zaminmc/zea/chat/render/ChatTabBar.class",
                        "net/zaminmc/zea/chat/render/ChatButtonBar.class",
                        "net/zaminmc/zea/chat/render/TradeStatusIndicator.class",
                        "net/zaminmc/zea/chat/render/TradeQueuePanel.class",
        };

        @Test
        void theChatWidgetsNeverOfferThemselvesAsKeyboardFocusTargets() throws IOException {
                refusesFocusNavigation("net/zaminmc/zea/chat/render/ChatTabBar.class");
                refusesFocusNavigation("net/zaminmc/zea/chat/render/ChatSearchBar.class");
                refusesFocusNavigation("net/zaminmc/zea/chat/render/ChatButtonBar.class");
                refusesFocusNavigation("net/zaminmc/zea/chat/render/TradeStatusIndicator.class");
                refusesFocusNavigation("net/zaminmc/zea/chat/render/TradeQueuePanel.class");
        }

        @Test
        void theMouseDrivenWidgetsNeverTakeFocusFromTheChatInput() throws IOException {
                for (String classFile : MOUSE_DRIVEN_WIDGETS) {
                        refusesFocusAfterClick(classFile);
                }
        }

        private static void refusesFocusNavigation(String classFile) throws IOException {
                assumeTrue(Files.isDirectory(MAIN_CLASSES), "compiled classes not found — run through Gradle");
                MethodNode override = method(classFile, "nextFocusPath");

                boolean pushedNull = false;
                for (AbstractInsnNode insn : override.instructions) {
                        int opcode = insn.getOpcode();
                        if (opcode == Opcodes.ACONST_NULL) {
                                pushedNull = true;
                        } else if (opcode == Opcodes.ARETURN) {
                                // The single return of a null-only method.
                        } else if (opcode == -1) {
                                // Frame and line-number pseudo-instructions.
                        } else {
                                fail(classFile + "#nextFocusPath does more than return null —"
                                                                + " it may answer a focus navigation request");
                        }
                }
                assertTrue(pushedNull, classFile + "#nextFocusPath returns something other than null");
        }

        private static void refusesFocusAfterClick(String classFile) throws IOException {
                assumeTrue(Files.isDirectory(MAIN_CLASSES), "compiled classes not found — run through Gradle");
                MethodNode override = method(classFile, "shouldTakeFocusAfterInteraction");

                boolean pushedFalse = false;
                for (AbstractInsnNode insn : override.instructions) {
                        int opcode = insn.getOpcode();
                        if (opcode == Opcodes.ICONST_0) {
                                pushedFalse = true;
                        } else if (opcode == Opcodes.IRETURN) {
                                // The single return of a false-only method.
                        } else if (opcode == -1) {
                                // Frame and line-number pseudo-instructions.
                        } else {
                                fail(classFile + "#shouldTakeFocusAfterInteraction does more than return false —"
                                                                + " a click can move the caret off the chat input");
                        }
                }
                assertTrue(pushedFalse, classFile + "#shouldTakeFocusAfterInteraction returns true");
        }

        private static MethodNode method(String classFile, String name) throws IOException {
                ClassNode node = new ClassNode(Opcodes.ASM9);
                new ClassReader(Files.readAllBytes(MAIN_CLASSES.resolve(classFile))).accept(node, 0);
                for (MethodNode method : node.methods) {
                        if (name.equals(method.name)) {
                                return method;
                        }
                }
                fail(classFile + " does not override " + name + " — 26.3's focus machinery can reach it");
                return null;
        }
}
