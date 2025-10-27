/*
 * Copyright (c) 2019-2024 GeyserMC. http://geysermc.org
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 *
 * @author GeyserMC
 * @link https://github.com/GeyserMC/GeyserConnect
 */

package org.geysermc.extension.connect;

import org.cloudburstmc.protocol.bedrock.data.AttributeData;
import org.cloudburstmc.protocol.bedrock.packet.BedrockPacketHandler;
import org.cloudburstmc.protocol.bedrock.packet.NetworkStackLatencyPacket;
import org.cloudburstmc.protocol.bedrock.packet.ResourcePackChunkRequestPacket;
import org.cloudburstmc.protocol.bedrock.packet.ResourcePackClientResponsePacket;
import org.cloudburstmc.protocol.bedrock.packet.SetLocalPlayerAsInitializedPacket;
import org.cloudburstmc.protocol.bedrock.packet.UpdateAttributesPacket;
import org.cloudburstmc.protocol.common.PacketSignal;
import org.geysermc.extension.connect.config.VirtualHostSection;
import org.geysermc.extension.connect.ui.UIHandler;
import org.geysermc.extension.connect.utils.Server;
import org.geysermc.extension.connect.utils.ServerManager;
import org.geysermc.extension.connect.utils.Utils;
import org.geysermc.geyser.entity.attribute.GeyserAttributeType;
import org.geysermc.geyser.network.UpstreamPacketHandler;
import org.geysermc.geyser.session.GeyserSession;
import org.geysermc.geyser.util.DimensionUtils;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class PacketHandler extends UpstreamPacketHandler {

    private final GeyserSession session;
    private final GeyserConnect geyserConnect;
    private final BedrockPacketHandler originalPacketHandler;

    public PacketHandler(GeyserConnect geyserConnect, GeyserSession session, BedrockPacketHandler packetHandler) {
        super(session.getGeyser(), session);

        this.session = session;
        this.geyserConnect = geyserConnect;
        this.originalPacketHandler = packetHandler;

        // Spawn the player in the end (it just looks better)
        DimensionUtils.setBedrockDimension(session, 2);
    }

    @Override
    public void onDisconnect(String reason) {
        // The user has disconnected without having connected to an actual server. If they have connected to
        // a server (transfer packet or geyser proxy), then the original packet handler has been restored.
        ServerManager.unloadServers(session);
        originalPacketHandler.onDisconnect(reason);
    }

    @Override
    public PacketSignal handle(SetLocalPlayerAsInitializedPacket packet) {
        geyserConnect.logger().debug("Player initialized: " + Utils.displayName(session));

        // Handle the virtual host if specified
        VirtualHostSection vhost = geyserConnect.config().vhost();
        if (vhost.enabled()) {
            String hostname = session.getClientData().getServerAddress();
            String[] hostparts = hostname.split(":");
            String host = hostparts[0];
            VHostDomainEntry entry = vhost.domains().get(host);
            if (entry != null) {
                String address = entry.getAddress();
                int port = entry.getPort();
                boolean online = true;
                boolean bedrock = entry.isBedrock();
                geyserConnect.logger().info(Utils.displayName(session) + " is using virtualhost " + host + " , and connect to " + address + ":" + port + (!bedrock ? " (java)" : " (bedrock)"));
                Utils.sendToServer(session, originalPacketHandler, new Server(address, port, online, bedrock, null, null, null));
                return PacketSignal.HANDLED;
            }
        }

        // Handle normal connections
        if (session.getPlayerEntity().getGeyserId() == packet.getRuntimeEntityId()) {
            if (!session.getUpstream().isInitialized()) {
                session.getUpstream().setInitialized(true);

                // Load the players servers
                ServerManager.loadServers(session);

                UIHandler uiHandler = new UIHandler(session, originalPacketHandler);
                uiHandler.initialiseSession();
            }
        }

        return PacketSignal.HANDLED;
    }

    @Override
    public PacketSignal handle(NetworkStackLatencyPacket packet) {
        // This is to fix a bug in the client where it doesn't load form images
        UpdateAttributesPacket updateAttributesPacket = new UpdateAttributesPacket();
        updateAttributesPacket.setRuntimeEntityId(1);
        List<AttributeData> attributes = Collections.singletonList(GeyserAttributeType.EXPERIENCE_LEVEL.getAttribute(0f));
        updateAttributesPacket.setAttributes(attributes);

        // Doesn't work 100% of the time but fixes it most of the time
        session.getGeyser().getScheduledThread().schedule(() -> session.sendUpstreamPacket(updateAttributesPacket), 500, TimeUnit.MILLISECONDS);

        return super.handle(packet);
    }

    @Override
    public PacketSignal handle(ResourcePackClientResponsePacket packet) {
        return originalPacketHandler.handle(packet); // relies on state in the original handler
    }

    @Override
    public PacketSignal handle(ResourcePackChunkRequestPacket packet) {
        return originalPacketHandler.handle(packet); // relies on state in the original handler
    }
}

