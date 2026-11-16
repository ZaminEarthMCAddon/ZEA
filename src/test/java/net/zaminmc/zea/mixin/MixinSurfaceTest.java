package net.zaminmc.zea.mixin;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.gui.components.CommandSuggestions;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.AbstractRecipeBookScreen;
import net.minecraft.client.gui.Hud;
import net.minecraft.client.DeltaTracker;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.client.multiplayer.chat.GuiMessageSource;
import net.minecraft.client.multiplayer.chat.GuiMessageTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MessageSignature;
import org.joml.Matrix3x2fStack;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Pins every mixin's target surface to members the target classes actually
 * declare in the pinned Minecraft version. Mixin resolves {@code @Shadow}
 * members and injection points only against the target class's own declared
 * members — never inherited ones: shadowing {@code Screen#addRenderableWidget}
 * from a mixin into {@code ChatScreen} crashed every launch until dev.5
 * (see WORKLOG 2026-11-09). These tests turn that class of breakage into a
 * build failure instead of a game crash.
 *
 * <p>When a mixin here changes, pin its new targets in the same commit.
 */
class MixinSurfaceTest {
        @Test
        void chatScreenMembersTargetedByChatScreenMixinAreDeclaredByChatScreen() {
                declaredField(ChatScreen.class, EditBox.class, "input");
                declaredMethod(ChatScreen.class, "init");
                declaredMethod(ChatScreen.class, "keyPressed", KeyEvent.class);
                declaredMethod(ChatScreen.class, "removed");
                declaredMethod(ChatScreen.class, "mouseClicked", MouseButtonEvent.class, boolean.class);
                // The render path the focus-loss search close polls.
                declaredMethod(ChatScreen.class, "extractRenderState",
                                GuiGraphicsExtractor.class, int.class, int.class, float.class);
        }

        @Test
        void addRenderableWidgetIsDeclaredByScreenItself() {
                // The generic method's erased view: parameter and return type
                // GuiEventListener. It must live on Screen for ScreenMixin's
                // shadow to resolve — the chat screen only inherits it.
                declaredMethod(Screen.class, "addRenderableWidget", GuiEventListener.class);
        }

        @Test
        void chatMembersTargetedByChatComponentMixinAreDeclaredByChatComponent() {
                declaredField(ChatComponent.class, int.class, "chatScrollbarPos");
                declaredField(ChatComponent.class, List.class, "trimmedMessages");
                declaredField(ChatComponent.class, boolean.class, "newMessageSinceScroll");
                declaredMethod(ChatComponent.class, "getScale");
                declaredMethod(ChatComponent.class, "getWidth");
                // The ingest path the retention wrap guards, and the private
                // store it calls for messages ZEA's filters hide.
                declaredMethod(ChatComponent.class, "addMessage",
                                Component.class, MessageSignature.class, GuiMessageSource.class, GuiMessageTag.class);
                declaredMethod(ChatComponent.class, "addMessageToDisplayQueue", GuiMessage.class);
                declaredMethod(ChatComponent.class, "addMessageToQueue", GuiMessage.class);
                // The bulk rebuild the scroll-stability bracket wraps.
                declaredMethod(ChatComponent.class, "refreshTrimmedMessages");
                // Where the chat forgets its messages — and ZEA its wrap memo.
                declaredMethod(ChatComponent.class, "clearMessages", boolean.class);
                declaredMethod(ChatComponent.class, "scrollChat", int.class);
                declaredMethod(ChatComponent.class, "resetChatScroll");
                declaredMethod(ChatComponent.class, "extractRenderState",
                                GuiGraphicsExtractor.class, Font.class,
                                int.class, int.class, int.class,
                                ChatComponent.DisplayMode.class, boolean.class);
                // The smooth-scroll surfaces: the private overload the render and
                // click-capture paths share, and the line walk whose three reads of
                // the anchor see the eased position and whose per-line accept call
                // carries the arrival fade. The calculator and consumer interfaces
                // are private to ChatComponent, so they load reflectively — the
                // same way the mixin's handler avoids naming them.
                declaredMethod(ChatComponent.class, "forEachLine",
                                nested("AlphaCalculator"), nested("LineConsumer"));
                declaredMethod(ChatComponent.class, "extractRenderState",
                                ChatComponent.ChatGraphicsAccess.class, int.class, int.class,
                                ChatComponent.DisplayMode.class);
                declaredMethod(nested("LineConsumer"), "accept",
                                GuiMessage.Line.class, int.class, float.class);
        }

        /** The private nested interface of ChatComponent the fade rides. */
        private static Class<?> nested(String name) {
                try {
                        return Class.forName("net.minecraft.client.gui.components.ChatComponent$" + name);
                } catch (ClassNotFoundException exception) {
                        throw new AssertionError(exception);
                }
        }

        @Test
        void chatScreenMembersTargetedByTheSearchFlowAreDeclaredByChatScreen() {
                // The suggestion popup the search silences on open and re-arms on
                // close — Chatting's exact open/close dance.
                declaredField(ChatScreen.class, CommandSuggestions.class, "commandSuggestions");
                declaredMethod(CommandSuggestions.class, "setAllowSuggestions", boolean.class);
                declaredMethod(CommandSuggestions.class, "updateCommandInfo");
                // The focus-navigation entry the Tab consumption sits in front of:
                // vanilla calls the parent's keyPressed after command completion,
                // and the injection point names that call.
                declaredMethod(Screen.class, "keyPressed", KeyEvent.class);
        }

        @Test
        void sendCommandTargetedByTheTradePacingGateIsDeclaredByClientPacketListener() {
                // The one choke point every outgoing command passes — the player's
                // own typing and recall, ZEA's channel routing and the trade
                // auto-send drain — where the trade pacing gate is enforced.
                declaredMethod(ClientPacketListener.class, "sendCommand", String.class);
        }

        @Test
        void hotbarSlotPassTargetedByHudMixinIsDeclaredByHud() {
                // The private slot pass the HUD draws every hotbar and off-hand
                // item through — the rarity wash hooks its head so the item,
                // count and durability bar stay on top.
                declaredMethod(Hud.class, "extractSlot", GuiGraphicsExtractor.class,
                                int.class, int.class, DeltaTracker.class, Player.class,
                                ItemStack.class, int.class);
        }

        @Test
        void mentionHighlightWrapsMembersDeclaredByChatComponentAndJoml() {
                // The line-background lambda and the interface method it calls.
                declaredMethod(ChatComponent.class, "lambda$extractRenderState$1",
                                int.class, int.class, ChatComponent.ChatGraphicsAccess.class,
                                int.class, float.class, GuiMessage.Line.class, int.class, float.class);
                declaredMethod(ChatComponent.ChatGraphicsAccess.class, "fill",
                                int.class, int.class, int.class, int.class, int.class);
                // The pose push ChatComponentMixin's smooth-chat wrap redirects.
                declaredMethod(Matrix3x2fStack.class, "pushMatrix");
        }

        @Test
        void containerMembersTargetedByContainerScreenMixinAreDeclaredByAbstractContainerScreen() {
                declaredField(AbstractContainerScreen.class, int.class, "leftPos");
                declaredField(AbstractContainerScreen.class, int.class, "topPos");
                declaredField(AbstractContainerScreen.class, int.class, "imageWidth");
                declaredField(AbstractContainerScreen.class, int.class, "imageHeight");
                declaredField(AbstractContainerScreen.class, boolean.class, "isQuickCrafting");
                declaredField(AbstractContainerScreen.class, AbstractContainerMenu.class, "menu");
                declaredMethod(AbstractContainerScreen.class, "init");
                declaredMethod(AbstractContainerScreen.class, "extractContents",
                                GuiGraphicsExtractor.class, int.class, int.class, float.class);
                declaredMethod(AbstractContainerScreen.class, "extractSlot",
                                GuiGraphicsExtractor.class, Slot.class, int.class, int.class);
                declaredMethod(AbstractContainerScreen.class, "extractTooltip",
                                GuiGraphicsExtractor.class, int.class, int.class);
                declaredMethod(AbstractContainerScreen.class, "getTooltipFromContainerItem", ItemStack.class);
                declaredMethod(AbstractContainerScreen.class, "keyPressed", KeyEvent.class);
                declaredMethod(AbstractContainerScreen.class, "mouseClicked", MouseButtonEvent.class, boolean.class);
                declaredMethod(AbstractContainerScreen.class, "mouseDragged", MouseButtonEvent.class,
                                double.class, double.class);
                declaredMethod(AbstractContainerScreen.class, "mouseReleased", MouseButtonEvent.class);
                declaredMethod(AbstractContainerScreen.class, "removed");
                // The lock/binding interception point: every click path funnels here.
                declaredMethod(AbstractContainerScreen.class, "slotClicked",
                                Slot.class, int.class, int.class, ContainerInput.class);
                // Drag membership: locked slots are filtered out of quick-crafting.
                declaredMethod(AbstractContainerScreen.class, "shouldAddSlotToQuickCraft",
                                Slot.class, ItemStack.class);
        }

        @Test
        void editBoxMembersTargetedByEditBoxAccessorAreDeclaredByEditBox() {
                // The scroll state the search bar's calculator overlay reads
                // to place its result after the visible text.
                declaredField(EditBox.class, int.class, "displayPos");
                declaredField(EditBox.class, int.class, "textX");
        }

        @Test
        void recipeBookComponentIsDeclaredByAbstractRecipeBookScreen() {
                declaredField(AbstractRecipeBookScreen.class,
                                net.minecraft.client.gui.screens.recipebook.RecipeBookComponent.class,
                                "recipeBookComponent");
        }

        @Test
        void dropItemIsDeclaredByMultiPlayerGameMode() {
                declaredMethod(net.minecraft.client.multiplayer.MultiPlayerGameMode.class, "dropItem",
                                net.minecraft.client.player.LocalPlayer.class, boolean.class);
        }

        private static void declaredMethod(Class<?> owner, String name, Class<?>... parameterTypes) {
                try {
                        owner.getDeclaredMethod(name, parameterTypes);
                } catch (NoSuchMethodException e) {
                        fail(owner.getName() + " does not declare " + name + Arrays.toString(parameterTypes)
                                        + " — re-verify the mixin targeting it against the mapped jar");
                }
        }

        private static void declaredField(Class<?> owner, Class<?> type, String name) {
                try {
                        Field field = owner.getDeclaredField(name);
                        assertEquals(type, field.getType(), owner.getName() + '#' + name + " changed type");
                } catch (NoSuchFieldException e) {
                        fail(owner.getName() + " does not declare field '" + name
                                        + "' — re-verify the mixin targeting it against the mapped jar");
                }
        }
}
