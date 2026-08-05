package com.seqwawa.seq.managers;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.InetAddress;
import org.junit.jupiter.api.Test;

class SafeRemoteUrlTest {
    @Test
    void rejectsPrivateAndLoopbackAddresses() throws Exception {
        assertFalse(SafeRemoteUrl.isPublic(InetAddress.getByName("127.0.0.1")));
        assertFalse(SafeRemoteUrl.isPublic(InetAddress.getByName("10.1.2.3")));
        assertFalse(SafeRemoteUrl.isPublic(InetAddress.getByName("172.20.1.2")));
        assertFalse(SafeRemoteUrl.isPublic(InetAddress.getByName("192.168.1.2")));
        assertFalse(SafeRemoteUrl.isPublic(InetAddress.getByName("fc00::1")));
    }

    @Test
    void permitsPublicAddresses() throws Exception {
        assertTrue(SafeRemoteUrl.isPublic(InetAddress.getByName("1.1.1.1")));
        assertTrue(SafeRemoteUrl.isPublic(InetAddress.getByName("2606:4700:4700::1111")));
    }
}
