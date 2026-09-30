package com.seqwawa.seq.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class BridgeMediaTest {

    private static final String ATTACHMENT_PATH = "/attachments/1/2/cat.png";

    @Test
    void showsAnAttachmentAndLabelsTheLinkThatStandsForIt() {
        String link = "https://cdn.discordapp.com" + ATTACHMENT_PATH + "?ex=1";
        BridgeMedia.Presentation presentation = BridgeMedia.present(
                "look" + System.lineSeparator() + link,
                List.of("https://media.discordapp.net" + ATTACHMENT_PATH + "?ex=1"));

        BridgeMedia.Picture picture = presentation.pictures().getFirst();
        assertEquals(BridgeMedia.Kind.STILL, picture.kind());
        assertEquals(URI.create("https://media.discordapp.net" + ATTACHMENT_PATH + "?ex=1"), picture.fetch());
        assertEquals("[Image]", picture.label());
        assertEquals(Map.of(link, picture), presentation.linkLabels());
    }

    @Test
    void knowsAnAttachmentLinkedInAMessageThatDiscordThenEmbeddedThroughItsProxy() {
        // A link to an attachment, posted as text: Discord embeds it through the
        // external proxy, and it is still the same file.
        String link = "https://cdn.discordapp.com/attachments/1/2/gregory.gif";
        BridgeMedia.Presentation presentation = BridgeMedia.present("look " + link, List.of(
                "https://images-ext-1.discordapp.net/external/abc/https/cdn.discordapp.com/attachments/1/2/gregory.gif"));

        assertEquals(Map.of(link, presentation.pictures().getFirst()), presentation.linkLabels());
    }

    @Test
    void showsEveryLinkAsALabelNeverAsAnAddress() {
        assertEquals(BridgeMedia.LinkKind.GIF, BridgeMedia.linkKind("https://cdn.discordapp.com/attachments/1/2/a.gif?ex=1"));
        assertEquals(BridgeMedia.LinkKind.GIF, BridgeMedia.linkKind("https://klipy.com/gifs/cat-dance"));
        assertEquals(BridgeMedia.LinkKind.GIF, BridgeMedia.linkKind("https://tenor.com/view/cat-gif-1"));
        assertEquals(BridgeMedia.LinkKind.IMAGE, BridgeMedia.linkKind("https://example.com/photo.JPG"));
        assertEquals(BridgeMedia.LinkKind.OTHER, BridgeMedia.linkKind("https://youtube.com/watch?v=abc"));
        assertEquals(BridgeMedia.LinkKind.OTHER, BridgeMedia.linkKind("https://example.com/a.gif.exe"));
        assertEquals(
                "look [GIF] and [Link] here",
                BridgeMedia.hideLinks("look https://tenor.com/view/x and <https://evil.example/page> here"));
    }

    @Test
    void opensOnlyImageFilesFromDiscordAndItsGifSites() {
        assertTrue(BridgeMedia.isSafeMediaLink("https://cdn.discordapp.com/attachments/1/2/a.gif?ex=1&is=2&hm=3"));
        assertTrue(BridgeMedia.isSafeMediaLink("https://media.discordapp.net/attachments/1/2/a.avif?format=webp"));
        assertTrue(BridgeMedia.isSafeMediaLink("https://media.tenor.com/m7sX0dsT3kUAAAAC/cat.gif"));
        assertTrue(BridgeMedia.isSafeMediaLink("https://static.klipy.com/ii/a/b/c/d.webp"));

        assertFalse(BridgeMedia.isSafeMediaLink("https://example.com/cat.gif"), "a host of anyone's choosing");
        assertFalse(BridgeMedia.isSafeMediaLink("http://cdn.discordapp.com/attachments/1/2/a.gif"), "not HTTPS");
        assertFalse(BridgeMedia.isSafeMediaLink("https://cdn.discordapp.com/attachments/1/2/setup.exe"), "no image");
        assertFalse(BridgeMedia.isSafeMediaLink("https://cdn.discordapp.com/emojis/1.gif"), "not an attachment");
        assertFalse(BridgeMedia.isSafeMediaLink("https://user@cdn.discordapp.com/attachments/1/2/a.gif"));
        assertFalse(BridgeMedia.isSafeMediaLink("https://cdn.discordapp.com:8443/attachments/1/2/a.gif"));
        assertFalse(BridgeMedia.isSafeMediaLink("https://cdn.discordapp.com.evil.example/attachments/1/2/a.gif"));
        assertFalse(BridgeMedia.isSafeMediaLink("javascript:alert(1)"));
    }

    @Test
    void animatesAGifAttachment() {
        BridgeMedia.Picture picture = BridgeMedia.present(
                        "", List.of("https://media.discordapp.net/attachments/1/2/dance.gif"))
                .pictures()
                .getFirst();

        assertEquals(BridgeMedia.Kind.ANIMATED, picture.kind());
        assertEquals("[GIF]", picture.label());
        assertEquals("dance.gif", picture.name());
    }

    @Test
    void playsATenorGifFromTheGifItWasMadeFromRatherThanItsVideoOrPreview() {
        String tenorLink = "https://tenor.com/view/cat-dance-gif-123456";
        BridgeMedia.Presentation presentation = BridgeMedia.present(tenorLink, List.of(
                "https://images-ext-1.discordapp.net/external/abc/https/media.tenor.com/m7sX0dsT3kUAAAPo/cat-dance.mp4",
                "https://images-ext-2.discordapp.net/external/def/https/media.tenor.com/m7sX0dsT3kUAAAAe/cat-dance.png"));

        assertEquals(1, presentation.pictures().size(), "the video and its preview are one GIF");
        BridgeMedia.Picture picture = presentation.pictures().getFirst();
        assertEquals(BridgeMedia.Kind.ANIMATED, picture.kind());
        assertEquals(URI.create("https://media.tenor.com/m7sX0dsT3kUAAAAC/cat-dance.gif"), picture.fetch());
        assertEquals(
                Map.of(tenorLink, picture), presentation.linkLabels(), "a message that is only the link gives it to its GIF");
    }

    @Test
    void preferATenorGifEvenWhenItsStillPreviewComesFirst() {
        BridgeMedia.Picture picture = BridgeMedia.present("", List.of(
                        "https://images-ext-2.discordapp.net/external/def/https/media.tenor.com/m7sX0dsT3kUAAAAe/cat.png",
                        "https://images-ext-1.discordapp.net/external/abc/https/media.tenor.com/m7sX0dsT3kUAAAPo/cat.mp4"))
                .pictures()
                .getFirst();

        assertEquals(BridgeMedia.Kind.ANIMATED, picture.kind());
    }

    @Test
    void playsAGiphyGifFromTheGifItWasMadeFrom() {
        BridgeMedia.Picture picture = BridgeMedia.present("", List.of(
                        "https://images-ext-1.discordapp.net/external/abc/https/media0.giphy.com/media/Abc123/giphy.mp4"))
                .pictures()
                .getFirst();

        assertEquals(BridgeMedia.Kind.ANIMATED, picture.kind());
        assertEquals(URI.create("https://media0.giphy.com/media/Abc123/giphy.gif"), picture.fetch());
    }

    @Test
    void playsAKlipyGifFromTheAnimatedWebpBesideItsVideo() {
        // What Discord's GIF picker sends since Tenor closed: a Klipy page link, embedded
        // as a video with an animated WebP, both named at random in the item's folder.
        String klipyLink = "https://klipy.com/gifs/cat-dance";
        String folder = "https/static.klipy.com/ii/c3a19a0b747a76e98651f2b9a3cca5ff/c4/80/";
        BridgeMedia.Presentation presentation = BridgeMedia.present(klipyLink, List.of(
                "https://images-ext-1.discordapp.net/external/abc/" + folder + "XZbuzkLhzDf.mp4",
                "https://images-ext-1.discordapp.net/external/def/" + folder + "XtfNihJm.webp"));

        assertEquals(1, presentation.pictures().size());
        BridgeMedia.Picture picture = presentation.pictures().getFirst();
        assertEquals(BridgeMedia.Kind.ANIMATED, picture.kind());
        assertEquals("[GIF]", picture.label());
        assertEquals(
                URI.create("https://static.klipy.com/ii/c3a19a0b747a76e98651f2b9a3cca5ff/c4/80/XtfNihJm.webp"),
                picture.fetch(),
                "fetched from Klipy as Klipy made it");
        assertEquals(Map.of(klipyLink, picture), presentation.linkLabels());
    }

    @Test
    void showsAKlipyItemOnceWhateverFormsOfItAreSent() {
        String folder = "https://images-ext-1.discordapp.net/external/abc/https/static.klipy.com/ii/h/c4/80/";

        assertEquals(1, BridgeMedia.present("", List.of(folder + "A.webp", folder + "B.gif")).pictures().size());
    }

    @Test
    void readsWebpAsItIsAndAvifAsTheAnimatedWebpDiscordConvertsItTo() {
        BridgeMedia.Picture webp = BridgeMedia.present(
                        "", List.of("https://media.discordapp.net/attachments/1/2/photo.webp?ex=1"))
                .pictures()
                .getFirst();
        BridgeMedia.Picture avif = BridgeMedia.present(
                        "", List.of("https://cdn.discordapp.com/attachments/1/2/cat.avif?ex=1&is=2&hm=3&"))
                .pictures()
                .getFirst();

        assertEquals(URI.create("https://media.discordapp.net/attachments/1/2/photo.webp?ex=1"), webp.fetch());
        assertEquals(
                URI.create("https://media.discordapp.net/attachments/1/2/cat.avif?ex=1&is=2&hm=3&format=webp&animated=true"),
                avif.fetch(),
                "asked of the proxy, which converts, keeping its signature");
    }

    @Test
    void asksDiscordsProxyForAnotherFormatInPlaceOfTheOneAskedBefore() {
        URI webp = URI.create("https://media.discordapp.net/attachments/1/2/cat.avif?ex=1&format=webp&animated=true");

        assertEquals(
                URI.create("https://media.discordapp.net/attachments/1/2/cat.avif?ex=1&format=png"),
                BridgeMedia.convertedByDiscord(webp, "format=png"));
        assertEquals(null, BridgeMedia.convertedByDiscord(URI.create("https://static.klipy.com/ii/a/b/c/d.webp"), "format=png"),
                "only Discord converts");
    }

    @Test
    void neverFetchesFromAnythingButDiscordAndGifSites() {
        assertEquals(BridgeMedia.Presentation.NONE, BridgeMedia.present("", List.of(
                "https://example.com/attachments/1/2/cat.png",
                "http://media.discordapp.net/attachments/1/2/cat.png",
                "https://media.discordapp.net/other/cat.png",
                "https://images-ext-1.discordapp.net/external/abc/https/example.com/clip.mp4")));
    }

    @Test
    void showsAtMostThreePictures() {
        List<String> urls = List.of(
                "https://media.discordapp.net/attachments/1/1/a.png",
                "https://media.discordapp.net/attachments/1/2/b.png",
                "https://media.discordapp.net/attachments/1/3/c.png",
                "https://media.discordapp.net/attachments/1/4/d.png");

        assertEquals(BridgeMedia.MAX_PICTURES, BridgeMedia.present("", urls).pictures().size());
    }

    @Test
    void findsLinksWithOrWithoutTheBracketsThatHideAnEmbed() {
        List<BridgeMedia.Link> links = BridgeMedia.links("a <https://x.io/1.png> b https://y.io/2.gif");

        assertEquals(2, links.size());
        assertEquals("https://x.io/1.png", links.getFirst().url());
        assertEquals(2, links.getFirst().start());
        assertEquals("https://y.io/2.gif", links.getLast().url());
        assertTrue(links.getLast().end() == "a <https://x.io/1.png> b https://y.io/2.gif".length());
    }
}
