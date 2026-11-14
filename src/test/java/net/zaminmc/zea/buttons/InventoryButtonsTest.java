package net.zaminmc.zea.buttons;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class InventoryButtonsTest {
        @TempDir
        Path tempDir;

        private final List<String> sent = new ArrayList<>();

        private ButtonActivator activator() {
                return new ButtonActivator(sent::add, 0);
        }

        private InventoryButtons loadedWith(String json) throws IOException {
                Path file = tempDir.resolve("buttons.json");
                Files.writeString(file, json, UTF_8);
                return new InventoryButtons(file, activator());
        }

        @Test
        void loadsButtonsFromTheStore() throws IOException {
                InventoryButtons buttons = loadedWith("""
                                {
                                  "version": 2,
                                  "buttons": [
                                    { "name": "Home", "command": "town spawn", "icon": "minecraft:compass" }
                                  ]
                                }
                                """);

                assertEquals(1, buttons.buttons().size());
                assertEquals("Home", buttons.buttons().get(0).name());
        }

        @Test
        void missingFileYieldsNoButtons() {
                InventoryButtons buttons = new InventoryButtons(tempDir.resolve("absent.json"), activator());

                assertEquals(List.of(), buttons.buttons());
        }

        @Test
        void clickRunsTheButtonThroughTheActivator() throws IOException {
                InventoryButtons buttons = loadedWith("""
                                {
                                  "version": 2,
                                  "buttons": [
                                    { "name": "Home", "command": "town spawn", "icon": "minecraft:compass" }
                                  ]
                                }
                                """);
                InventoryButton home = buttons.buttons().get(0);

                assertEquals(ButtonActivator.Result.EXECUTED, buttons.click(home, 1_000));

                assertEquals(List.of("town spawn"), sent);
        }

        @Test
        void disabledButtonsDoNotRun() throws IOException {
                InventoryButtons buttons = loadedWith("""
                                {
                                  "version": 2,
                                  "buttons": [
                                    { "name": "Off", "command": "town spawn", "icon": "minecraft:compass",
                                      "enabled": false }
                                  ]
                                }
                                """);
                InventoryButton off = buttons.buttons().get(0);

                assertEquals(ButtonActivator.Result.INVALID, buttons.click(off, 1_000));
                assertEquals(List.of(), sent);
        }

        @Test
        void unconfiguredButtonsDoNotRun() throws IOException {
                InventoryButtons buttons = loadedWith("""
                                {
                                  "version": 2,
                                  "buttons": [
                                    { "name": "Template", "icon": "minecraft:chest" }
                                  ]
                                }
                                """);
                InventoryButton template = buttons.buttons().get(0);

                assertEquals(ButtonActivator.Result.INVALID, buttons.click(template, 1_000));
                assertEquals(List.of(), sent);
        }

        @Test
        void resetClearsCooldownsOnly() throws IOException {
                InventoryButtons buttons = loadedWith("""
                                {
                                  "version": 2,
                                  "buttons": [
                                    { "name": "Home", "command": "town spawn", "icon": "minecraft:compass" }
                                  ]
                                }
                                """);
                InventoryButton home = buttons.buttons().get(0);
                ButtonActivator slow = new ButtonActivator(sent::add, 10_000);
                InventoryButtons cooldown = new InventoryButtons(tempDir.resolve("buttons.json"), slow);

                assertEquals(ButtonActivator.Result.EXECUTED, cooldown.click(cooldown.buttons().get(0), 1_000));
                assertEquals(ButtonActivator.Result.COOLDOWN, cooldown.click(cooldown.buttons().get(0), 2_000));
                cooldown.reset();
                assertEquals(ButtonActivator.Result.EXECUTED, cooldown.click(cooldown.buttons().get(0), 3_000));
                assertNull(null);
        }
}
