package com.seqwawa.seq.network;

final class IncomingMessageRouter {
    private final ConnectionManager target;

    IncomingMessageRouter(ConnectionManager target) {
        this.target = target;
    }

    void route(IncomingMessageParser.IncomingMessage incoming) {
        switch (incoming.type()) {
            case TreasuryAuthResponse.CHALLENGE_TYPE -> target.handleTreasuryAuthChallenge(incoming.payload());
            case TreasuryAuthResponse.AUTHENTICATED_TYPE -> target.handleTreasuryAuthenticated(incoming.payload());
            case "authenticated" -> target.handleAuthenticated(incoming.payload());
            case "connected_users" -> target.handleConnectedUsers(incoming.payload());
            case "bomb_share_prompt" -> target.handleBombSharePrompt(incoming.payload());
            case "bomb_share_result" -> target.handleBombShareResult(incoming.payload());
            case "treasury_out_recorded" -> target.handleTreasuryOutRecorded(incoming.payload());
            case "guild_storage_snapshot" -> target.handleGuildStorageSnapshot(incoming.payload());
            case "discord_chat" -> target.handleDiscordChat(incoming.payload());
            case "party_finder_update" -> target.handlePartyFinderUpdate(incoming.payload());
            case "party_finder_invite" -> target.handlePartyFinderInvite(incoming.payload());
            case "party_finder_stale_warning" -> target.handlePartyFinderStaleWarning(incoming.payload());
            case "error" -> target.handleBackendError(incoming.payload());
            default -> target.handleUnhandledIncomingMessage(incoming.type());
        }
    }
}
