package net.zaminmc.zea.mixin;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * The live-reported container tooltip crash, pinned shut: an
 * {@code @Inject} handler that takes a {@link
 * org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable}
 * must declare {@code cancellable = true}. Vanilla's mixin applies the
 * flag at weave time — without it the generated callback is a plain
 * {@code CallbackInfo}, the handler's {@code cir.setReturnValue(...)}
 * throws an {@code IllegalArgumentException} the first frame the hook
 * fires, and the screen the hook belongs to crashes the game. That was
 * the rarity tooltip: two handlers reading their return value, editing
 * it and writing it back, neither cancellable — invisible in every
 * headless test because the weave never runs there, fatal the first
 * time a player hovered an item.
 *
 * <p>The invariant is stricter than strictly necessary — a handler that
 * never calls a mutator on the returnable could survive without the
 * flag — but a returnable in the signature announces the intent to
 * influence a return value, and the flag is what makes that intent
 * real. Every handler taking a returnable must therefore be cancellable,
 * and a handler that does not need one should not take one.
 */
class InjectionCancellableTest {
	private static final Path MAIN_CLASSES = Paths.get("build", "classes", "java", "main");
	private static final String INJECT_DESC =
			"Lorg/spongepowered/asm/mixin/injection/Inject;";
	private static final String RETURNABLE_INTERNAL =
			"org/spongepowered/asm/mixin/injection/callback/CallbackInfoReturnable";

	@Test
	void everyInjectHandlerTakingAReturnableIsCancellable() throws IOException {
		assumeTrue(Files.isDirectory(MAIN_CLASSES), "compiled classes not found — run through Gradle");

		List<String> violations = new ArrayList<>();
		try (var paths = Files.walk(MAIN_CLASSES)) {
			for (Path file : paths.filter(p -> p.toString().endsWith(".class")).toList()) {
				String internal = MAIN_CLASSES.relativize(file).toString()
						.replace('\\', '/').replace(".class", "");
				if (!internal.startsWith("net/zaminmc/zea/mixin/")) {
					continue;
				}
				ClassNode node = new ClassNode(Opcodes.ASM9);
				new ClassReader(Files.readAllBytes(file)).accept(node, 0);
				for (MethodNode method : node.methods) {
					AnnotationNode inject = injectAnnotation(method);
					if (inject == null || !takesReturnable(method)) {
						continue;
					}
					if (!isCancellable(inject)) {
						violations.add(internal + "#" + method.name
								+ " takes a CallbackInfoReturnable but is not cancellable");
					}
				}
			}
		}
		if (!violations.isEmpty()) {
			fail(String.join("\n", violations)
					+ "\nwithout cancellable = true the first frame that runs the hook"
					+ " crashes the game — the rarity tooltip crash");
		}
		assertTrue(violations.isEmpty());
	}

	private static AnnotationNode injectAnnotation(MethodNode method) {
		if (method.visibleAnnotations == null) {
			return null;
		}
		for (AnnotationNode annotation : method.visibleAnnotations) {
			if (INJECT_DESC.equals(annotation.desc)) {
				return annotation;
			}
		}
		return null;
	}

	private static boolean takesReturnable(MethodNode method) {
		for (org.objectweb.asm.Type type : org.objectweb.asm.Type.getArgumentTypes(method.desc)) {
			if (type.getInternalName().equals(RETURNABLE_INTERNAL)) {
				return true;
			}
		}
		return false;
	}

	private static boolean isCancellable(AnnotationNode inject) {
		if (inject.values == null) {
			return false;
		}
		for (int i = 0; i + 1 < inject.values.size(); i += 2) {
			if ("cancellable".equals(inject.values.get(i))) {
				return Boolean.TRUE.equals(inject.values.get(i + 1));
			}
		}
		return false;
	}
}
