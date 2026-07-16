package com.junzhecai.hmdp.utils;

//Redis常量管理类
public class RedisConstants {
    public static final String LOGIN_CODE_KEY = "loginOrRegister:code:";
    public static final Long LOGIN_CODE_TTL = 2L;
    public static final String LOGIN_USER_KEY = "loginOrRegister:token:";
    public static final Long LOGIN_USER_TTL = 5L;

    public static final Long CACHE_NULL_TTL = 1L;

    public static final Long CACHE_SHOP_TTL = 5L;
    public static final String CACHE_SHOP_KEY = "cache:shop:";
    public static final Long CACHE_SHOP_TYPE_TTL = 5L;
    public static final String CACHE_SHOP_TYPE_KEY = "cache:shop:type:";

    public static final String LOCK_SHOP_KEY = "lock:shop:";
    public static final Long LOCK_SHOP_TTL = 10L;

    public static String toLockKey(String cachePrefix) {
        return cachePrefix.replace("cache:", "lock:");
    }

    public static final String SECKILL_STOCK_KEY = "seckill:stock:";
    public static final String BLOG_LIKED_KEY = "blog:liked:";
    public static final String FEED_KEY = "feed:";
    public static final String SHOP_GEO_KEY = "shop:geo:";
    public static final String USER_SIGN_KEY = "sign:";
}
