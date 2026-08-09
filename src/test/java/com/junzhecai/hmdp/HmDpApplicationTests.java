package com.junzhecai.hmdp;

import com.junzhecai.hmdp.model.entity.Shop;
import com.junzhecai.hmdp.service.Impl.ShopServiceImpl;
import com.junzhecai.hmdp.utils.RedisIdWorker;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static com.junzhecai.hmdp.utils.RedisConstants.SHOP_GEO_KEY;

@SpringBootTest
class HmDpApplicationTests {
    @Autowired
    private ShopServiceImpl shopService;
    @Autowired
    private RedisIdWorker redisIdWorker;
    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Test
    public void testSaveShop() throws InterruptedException {
        shopService.saveShop2Redis(1L, 10L);
    }

    private final ExecutorService es = Executors.newFixedThreadPool(500);

    @Test
    public void testIdWorker() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(300);
        Runnable task = () -> {
            for (int i = 0; i < 100; i++) {
                long id = redisIdWorker.nextId("order");
                System.out.println(id);
            }
            latch.countDown();
        };
        long begin = System.currentTimeMillis();
        for (int i = 0; i < 300; i++) {
            es.submit(task);
        }
        latch.await();
        long end = System.currentTimeMillis();
        System.out.println(end - begin);
    }

    @Test
    public void testLoadShopData() {
        //查询店铺信息
        List<Shop> list = shopService.list();
        //把店铺按照typeId分组
        //stream流
        //Map<Long, List<Shop>> map = list.stream().collect(Collectors.groupingBy(Shop::getTypeId));
        //普通for循环
        Map<Long, List<Shop>> map = new HashMap<>();
        for (Shop shop : list) {
            Long typeId = shop.getTypeId();
            List<Shop> shops = map.get(typeId);
            if (shops == null) {
                shops = new ArrayList<>();
                map.put(typeId, shops);
            }
            shops.add(shop);
        }
        //把数据写入Redis
        for (Map.Entry<Long, List<Shop>> entry : map.entrySet()) {
            //获取typeId
            Long typeId = entry.getKey();
            String key = SHOP_GEO_KEY + typeId;
            //获取同类型店铺的集合
            List<Shop> value = entry.getValue();
            List<RedisGeoCommands.GeoLocation<String>> locations = new ArrayList<>(value.size());
            for (Shop shop : value) {
                locations.add(new RedisGeoCommands.GeoLocation<>(
                        shop.getId().toString(),
                        new Point(shop.getX(), shop.getY())
                ));
            }
            stringRedisTemplate.opsForGeo().add(key, locations);
        }
    }
}
