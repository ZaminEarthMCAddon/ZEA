package net.zaminmc.zea.chat.classify;

import net.zaminmc.zea.chat.model.CapturedMessage;
import net.zaminmc.zea.chat.model.CrateLuckyMessage;
import net.zaminmc.zea.chat.model.ItemShowcaseMessage;
import net.zaminmc.zea.chat.model.MessageCategory;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The four advertisement-shaped categories, against the exact live
 * examples (plain text — capture flattens the formatting away) and
 * against look-alike ordinary messages that must stay unclassified.
 */
class AdvertisementClassifierTest {
        private MessageCategory classify(String text, MessageClassifier classifier) {
                CapturedMessage captured = CapturedMessage.of(Component.literal(text));
                return classifier.tryClassify(captured)
                                .map(result -> result.category())
                                .orElse(MessageCategory.UNCLASSIFIED);
        }

        @Test
        void crateLuckyAnnouncementIsRecognized() {
                assertEquals(MessageCategory.CRATE_LUCKY, classify(
                                "(hubbayes got lucky and received a bruh box /vote)",
                                new CrateLuckyClassifier()));
                var payload = new CrateLuckyClassifier().tryClassify(
                                CapturedMessage.of(Component.literal(
                                                "(hubbayes got lucky and received a bruh box /vote)")));
                assertEquals("hubbayes", payload.get().payload() instanceof CrateLuckyMessage lucky
                                ? lucky.player() : null);
                assertEquals("bruh box", payload.get().payload() instanceof CrateLuckyMessage lucky
                                ? lucky.crateName() : null);
        }

        @Test
        void crateLuckyRecognizesOtherPlayersAndCrates() {
                assertEquals(MessageCategory.CRATE_LUCKY, classify(
                                "(SomePlayer got lucky and received a vote key)",
                                new CrateLuckyClassifier()));
                assertEquals(MessageCategory.CRATE_LUCKY, classify(
                                "(SomePlayer got lucky and received an epic box /vote)",
                                new CrateLuckyClassifier()));
        }

        @Test
        void ordinaryTalkAboutCratesIsNotCrateLucky() {
                assertEquals(MessageCategory.UNCLASSIFIED, classify(
                                "I got lucky and received a hug",
                                new CrateLuckyClassifier()));
                assertEquals(MessageCategory.UNCLASSIFIED, classify(
                                "someone got lucky and received a bruh box yesterday",
                                new CrateLuckyClassifier()));
                assertEquals(MessageCategory.UNCLASSIFIED, classify(
                                "did you see hubbayes got a bruh box?",
                                new CrateLuckyClassifier()));
        }

        @Test
        void discordRecruitmentAdsAreRecognized() {
                assertEquals(MessageCategory.DISCORD_RECRUITMENT, classify(
                                "Premium [Discord] nf_miracle.: NEW PLAYERS! need a town, and want free tp, "
                                                + "diamonds and housing? Type /t join Berlin",
                                new DiscordRecruitmentClassifier()));
                assertEquals(MessageCategory.DISCORD_RECRUITMENT, classify(
                                "Mod [Discord] andre157: Lost or Lonely? New Players! If you want Teleport "
                                                + "perms, Gear and a GREAT town, than type : /t join Cairo",
                                new DiscordRecruitmentClassifier()));
        }

        @Test
        void discordRecruitmentSupportsOtherTownsAndSenders() {
                assertEquals(MessageCategory.DISCORD_RECRUITMENT, classify(
                                "[Discord] someone: New players! join /n join France now",
                                new DiscordRecruitmentClassifier()));
        }

        @Test
        void ordinaryDiscordAndMentionsAreNotRecruitment() {
                // A Discord forward without recruitment language is not recruitment.
                assertEquals(MessageCategory.UNCLASSIFIED, classify(
                                "[Discord] friend: hey everyone, new players welcome on our discord",
                                new DiscordRecruitmentClassifier()));
                assertEquals(MessageCategory.UNCLASSIFIED, classify(
                                "does anyone have the Discord link?",
                                new DiscordRecruitmentClassifier()));
                assertEquals(MessageCategory.UNCLASSIFIED, classify(
                                "Chatting with new players is fun",
                                new DiscordRecruitmentClassifier()));
        }

        @Test
        void promotionalBannersAreRecognized() {
                // The observed banner: centered, per-character formatted, blank rows around.
                assertEquals(MessageCategory.PROMO_BANNER, classify(
                                "\n\n                         /premium\n\n",
                                new PromoBannerClassifier()));
                assertEquals(MessageCategory.PROMO_BANNER, classify("/premium",
                                new PromoBannerClassifier()));
        }

        @Test
        void ordinaryMessagesMentioningCommandsAreNotBanners() {
                assertEquals(MessageCategory.UNCLASSIFIED, classify(
                                "type /premium to support the server guys",
                                new PromoBannerClassifier()));
                assertEquals(MessageCategory.UNCLASSIFIED, classify(
                                "why does /premium exist",
                                new PromoBannerClassifier()));
                assertEquals(MessageCategory.UNCLASSIFIED, classify(
                                "/premium run it twice\nsecond line",
                                new PromoBannerClassifier()));
        }

        @Test
        void itemShowcaseAdsAreRecognized() {
                // The observed message: item placeholder (\uFFFC), emojis, nation|town, holder, renamed item.
                assertEquals(MessageCategory.ITEM_SHOWCASE, classify(
                                "\uFFFC☄🐇 [Germany|Bremen] axieyoel is holding [Dragon]",
                                new ItemShowcaseClassifier()));
                var payload = new ItemShowcaseClassifier().tryClassify(CapturedMessage.of(
                                Component.literal("\uFFFC☄🐇 [Germany|Bremen] axieyoel is holding [Dragon]")));
                assertEquals("Dragon", ((ItemShowcaseMessage) payload.get().payload()).itemName());
                assertEquals("axieyoel", ((ItemShowcaseMessage) payload.get().payload()).holder());
        }

        @Test
        void itemShowcaseSupportsExplicitItemLabels() {
                assertEquals(MessageCategory.ITEM_SHOWCASE, classify(
                                "[item/totem_of_undying@items] [France|Paris] otheruser is holding [Sword of Dawn]",
                                new ItemShowcaseClassifier()));
        }

        @Test
        void ordinaryItemMessagesAreNotShowcaseAds() {
                // A linked item without the "is holding [x]" tail stays unclassified.
                assertEquals(MessageCategory.UNCLASSIFIED, classify(
                                "\uFFFC look at this pickaxe I found",
                                new ItemShowcaseClassifier()));
                // "is holding" prose without an item component stays unclassified.
                assertEquals(MessageCategory.UNCLASSIFIED, classify(
                                "he is holding [a grudge] against the whole town",
                                new ItemShowcaseClassifier()));
        }
}
