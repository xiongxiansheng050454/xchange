package com.xchange.platform.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

@Slf4j
@Configuration
public class RedisConfig {

    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory factory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(factory);

        // Key 序列化
        template.setKeySerializer(new StringRedisSerializer());

        // Value 序列化
        template.setValueSerializer(new GenericJackson2JsonRedisSerializer());

        // Hash Key 序列化
        template.setHashKeySerializer(new StringRedisSerializer());

        // Hash Value 序列化
        template.setHashValueSerializer(new GenericJackson2JsonRedisSerializer());

        template.afterPropertiesSet();
        return template;
    }

    // 新增：为Lua脚本专门创建的String模板（使用不同名称）
    @Bean("stringRedisTemplateForLua")
    public StringRedisTemplate stringRedisTemplateForLua(RedisConnectionFactory factory) {
        StringRedisTemplate template = new StringRedisTemplate();
        template.setConnectionFactory(factory);
        // 关键：关闭事务支持（Lua脚本不需要）
        template.setEnableTransactionSupport(false);
        return template;
    }

    /**
     * Redis监听器容器（简化配置，移除不兼容方法）
     */
    @Bean
    public RedisMessageListenerContainer redisMessageListenerContainer(
            RedisConnectionFactory connectionFactory) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        log.info("RedisMessageListenerContainer 初始化完成");
        return container;
    }

    @Bean
    public RedisScript<Long> stockDeductScript() {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();

        // 纯Java字符串定义（确保格式绝对正确）
        String luaScript =
                "local key = KEYS[1];\n" +
                        "local deduct = tonumber(ARGV[1]);\n" +
                        "local stock = redis.call('GET', key);\n" +
                        "if not stock then return -1 end;\n" +
                        "stock = tonumber(stock);\n" +
                        "if stock < deduct then return -2 end;\n" +
                        "local newStock = redis.call('DECRBY', key, deduct);\n" +
                        "return newStock;";

        script.setScriptText(luaScript);
        script.setResultType(Long.class);
        return script;
    }
}