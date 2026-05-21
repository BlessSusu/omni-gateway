package com.omni.gateway.core.plugin;

import com.omni.gateway.core.auth.AuthResult;
import com.omni.gateway.core.model.CommandEnvelope;
import com.omni.gateway.core.model.ThingModel;
import com.omni.gateway.core.session.DeviceSession;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandler;

import java.util.List;
import java.util.Optional;

/**
 * 协议插件：探测、Pipeline、鉴权、物模型、下行编码。
 */
public interface ProtocolPlugin {

    String pluginId();

    int minProbeLength();

    /**
     * 帧头/特征匹配；数据不足下限时应返回 false（非失败）。
     */
    boolean detect(ByteBuf buffer);

    List<ChannelHandler> createHandlers(DeviceSession session);

    AuthResult authenticate(DeviceSession session, Object firstMessage);

    Optional<ThingModel> toThingModel(DeviceSession session, Object protocolMessage);

    Optional<ByteBuf> encodeDownlink(DeviceSession session, CommandEnvelope command);

    /**
     * 上行消息是否匹配某条待确认下行的 ACK。
     */
    default boolean matchDownlinkAck(DeviceSession session, Object protocolMessage) {
        return false;
    }

    DownlinkAckMode downlinkAckMode();

    /** 鉴权成功后可选下发的响应帧（如 auth_ok）。 */
    default Optional<ByteBuf> buildAuthSuccessResponse(DeviceSession session) {
        return Optional.empty();
    }

    enum DownlinkAckMode {
        FIRE_AND_FORGET,
        WAIT_DEVICE_ACK
    }
}
