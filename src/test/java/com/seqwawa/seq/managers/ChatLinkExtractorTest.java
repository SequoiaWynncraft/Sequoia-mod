package com.seqwawa.seq.managers;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.net.URI;
import org.junit.jupiter.api.Test;

class ChatLinkExtractorTest {
    @Test
    void stripsSentencePunctuationButPreservesBalancedParentheses() {
        assertEquals(
                java.util.List.of(URI.create("https://example.com/wiki/Test_(raid)")),
                ChatLinkExtractor.extract("look at https://example.com/wiki/Test_(raid).", 2));
    }

    @Test
    void deduplicatesLinksAndHonorsLimit() {
        assertEquals(
                java.util.List.of(URI.create("https://example.com/a"), URI.create("https://example.com/b")),
                ChatLinkExtractor.extract(
                        "https://example.com/a https://example.com/a https://example.com/b https://example.com/c",
                        2));
    }

    @Test
    void ignoresNonWebAndMalformedLinks() {
        assertEquals(java.util.List.of(), ChatLinkExtractor.extract("ftp://example.com http://[broken", 2));
    }
}
