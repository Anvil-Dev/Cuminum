package dev.anvilcraft.resource.cuminum.example

import dev.anvilcraft.resource.cuminum.codec.AutoCodec
import dev.anvilcraft.resource.cuminum.codec.CodecField
import dev.anvilcraft.resource.cuminum.network.AutoStreamCodec
import dev.anvilcraft.resource.cuminum.network.StreamCodecField
import com.mojang.serialization.Codec

/**
 * 示例：使用 @AutoCodec 注解
 * 
 * IDE 将自动为此类生成以下虚拟字段：
 * - public static final Codec<Player> CODEC
 */
@AutoCodec
data class Player(
    @CodecField("player_id")
    val id: Int,
    
    @CodecField("player_name")
    val name: String,
    
    @CodecField(value = "level")
    val level: Int = 1
) {
    companion object {
        // IDE 会自动识别这个虚拟字段
        // val CODEC: Codec<Player> = ...
    }
}

/**
 * 示例：使用 @AutoCodec(CodecType.MAP_CODEC)
 * 
 * IDE 将自动为此类生成以下虚拟字段：
 * - public static final MapCodec<Config> MAP_CODEC
 */
@AutoCodec(dev.anvilcraft.resource.cuminum.codec.AutoCodec.CodecType.MAP_CODEC)
data class Config(
    val timeout: Int,
    val retries: Int,
    val host: String
)

/**
 * 示例：使用 @AutoCodec(CodecType.BOTH)
 * 
 * IDE 将自动为此类生成以下虚拟字段：
 * - public static final Codec<Settings> CODEC
 * - public static final MapCodec<Settings> MAP_CODEC
 */
@AutoCodec(dev.anvilcraft.resource.cuminum.codec.AutoCodec.CodecType.BOTH)
data class Settings(
    val debug: Boolean,
    val logLevel: String
)

/**
 * 示例：使用 @AutoStreamCodec
 * 
 * IDE 将自动为此类生成以下虚拟字段：
 * - public static final StreamCodec<ByteBuf, Packet> STREAM_CODEC
 */
@AutoStreamCodec
data class Packet(
    @StreamCodecField
    val packetId: Int,
    
    @StreamCodecField
    val data: String
) {
    companion object {
        // IDE 会自动识别这个虚拟字段
        // val STREAM_CODEC: StreamCodec<ByteBuf, Packet> = ...
    }
}

/**
 * 使用示例
 */
object CodecUsageExample {
    fun serializePlayer(player: Player): String {
        // IDE 会提供 CODEC 补全建议
        return Player.CODEC.encodeStart(com.mojang.serialization.JsonOps.INSTANCE, player)
            .resultOrPartial { error -> println("序列化失败: $error") }
            .map { obj -> obj.toString() }
            .orElse(null)
    }

    fun deserializePlayer(json: String): Player? {
        // IDE 会识别 Player.CODEC 并提供类型检查
        return Player.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE, null)
            .resultOrPartial { error -> println("反序列化失败: $error") }
            .orElse(null)
    }

    fun serializeConfig(config: Config): String {
        // IDE 会识别 MAP_CODEC 字段
        val mapCodec = Config.MAP_CODEC
        return mapCodec.encode(config, com.mojang.serialization.JsonOps.INSTANCE, com.mojang.serialization.JsonOps.INSTANCE.empty())
            .resultOrPartial { error -> println("编码失败: $error") }
            .map { obj -> obj.toString() }
            .orElse(null)
    }

    fun networkSend(packet: Packet) {
        // IDE 会识别 STREAM_CODEC 字段
        val codec = Packet.STREAM_CODEC
        // 使用 codec 编码网络包...
    }
}

/**
 * IDE 功能演示：
 * 
 * 1. 代码补全 (Ctrl+Space)
 *    - 在 Player 类中输入，会看到 CODEC 的补全建议
 * 
 * 2. 导航 (Ctrl+Click / Cmd+Click)
 *    - 点击 Player.CODEC 会跳转到 Player 类定义
 * 
 * 3. 快速文档 (Ctrl+Q / Cmd+J)
 *    - 显示 "生成的 Codec 字段，用于 Player 的序列化和反序列化"
 * 
 * 4. 类型检查
 *    - 确保 CODEC 的类型为 Codec<Player>，提供类型安全
 * 
 * 5. 查找使用 (Ctrl+Shift+F / Cmd+Shift+F)
 *    - 查找所有使用 CODEC 的地方
 */

