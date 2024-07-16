package com.dd.redislock.service;

import cn.hutool.core.util.IdUtil;
import com.dd.redislock.myLock.DistributedLockFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

@Service
@Slf4j
public class InventoryService {
    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    @Value("${server.port}")
    private String port;
    @Autowired
    private DistributedLockFactory distributedLockFactory;

    private Lock lock = new ReentrantLock();

    /**
     * V3.0 lock+jmeter压测后 出现超卖现象
     */
    /*public String sale()
    {
        String retMessage = "";
        lock.lock();
        try
        {
            //1 查询库存信息
            String result = stringRedisTemplate.opsForValue().get("inventory001");
            //2 判断库存是否足够
            Integer inventoryNumber = result == null ? 0 : Integer.parseInt(result);
            //3 扣减库存
            if(inventoryNumber > 0) {
                stringRedisTemplate.opsForValue().set("inventory001",String.valueOf(--inventoryNumber));
                retMessage = "成功卖出一个商品，库存剩余: "+inventoryNumber;
                System.out.println(retMessage);
            }else{
                retMessage = "商品卖完了，o(╥﹏╥)o";
            }
        }finally {
            lock.unlock();
        }
        return retMessage+"\t"+"服务端口号："+port;
    }*/

    /**
     * V3.1 递归重试 容易导致StackOverflowError 不太推荐
     * 高并发唤醒后推荐用while判断而不是用if
     */
    /*public String sale() {
        String retMessage = "";
        String key = "ddRedisLock";
        String uuidValue = IdUtil.simpleUUID() + ":" + Thread.currentThread().getId();

        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, uuidValue);
        if (!flag) {
            try {
                TimeUnit.MILLISECONDS.sleep(20);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            //暂停20毫秒后 进行递归重试获取锁
            sale();
        } else {
            try {
                //1 查询库存信息
                String result = stringRedisTemplate.opsForValue().get("inventory001");
                //2 判断库存是否足够
                Integer inventoryNumber = result == null ? 0 : Integer.parseInt(result);
                //3 扣减库存
                if(inventoryNumber > 0) {
                    stringRedisTemplate.opsForValue().set("inventory001",String.valueOf(--inventoryNumber));
                    retMessage = "成功卖出一个商品，库存剩余: "+inventoryNumber;
                    System.out.println(retMessage);
                }else{
                    retMessage = "商品卖完了，o(╥﹏╥)o";
                }
            } finally {
                stringRedisTemplate.delete(key);
            }
        }
        return retMessage+"\t"+"服务端口号："+port;
    }*/

    /**
     * V3.2 存在的问题
     * 部署了微服务的Java程序机器挂了，代码层面根本没有走到finally这块，
     * 没办法保证解锁(无过期时间该key一直存在)，这个key没有被删除，需要加入一个过期时间限定key
     */
    /*public String sale() {
        String retMessage = "";
        String key = "ddRedisLock";
        String uuidValue = IdUtil.simpleUUID() + ":" + Thread.currentThread().getId();

        // 用while来替代if 且用自旋替代递归
        while (!stringRedisTemplate.opsForValue().setIfAbsent(key, uuidValue)) {
            try {
                TimeUnit.MILLISECONDS.sleep(20);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
        // 抢锁成功的请求线程 进行正常的业务逻辑 扣减库存
        try {
            //1 查询库存信息
            String result = stringRedisTemplate.opsForValue().get("inventory001");
            //2 判断库存是否足够
            Integer inventoryNumber = result == null ? 0 : Integer.parseInt(result);
            //3 扣减库存
            if (inventoryNumber > 0) {
                stringRedisTemplate.opsForValue().set("inventory001", String.valueOf(--inventoryNumber));
                retMessage = "成功卖出一个商品，库存剩余: " + inventoryNumber;
                System.out.println(retMessage);
            } else {
                retMessage = "商品卖完了，o(╥﹏╥)o";
            }
        }finally {
            stringRedisTemplate.delete(key);
        }
        return retMessage + "\t" + "服务端口号：" + port;
    }*/

    /**
     * V4.0 存在问题：
     * stringRedisTemplate.delete(key);只能自己删除自己的锁 不能删除别人的锁，需要添加判断是否是自己的锁来进行操作
     */
    /*public String sale() {
        String retMessage = "";
        String key = "ddRedisLock";
        String uuidValue = IdUtil.simpleUUID() + ":" + Thread.currentThread().getId();

        // 改进点 加锁和过期时间设置必须在同一行 保证原子性
        while (!stringRedisTemplate.opsForValue().setIfAbsent(key, uuidValue, 30L, TimeUnit.SECONDS)) {
            try {
                TimeUnit.MILLISECONDS.sleep(20);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }

        //stringRedisTemplate.expire(key, 30L, TimeUnit.SECONDS);
        // 抢锁成功的请求线程 进行正常的业务逻辑 扣减库存
        try {
            //1 查询库存信息
            String result = stringRedisTemplate.opsForValue().get("inventory001");
            //2 判断库存是否足够
            Integer inventoryNumber = result == null ? 0 : Integer.parseInt(result);
            //3 扣减库存
            if (inventoryNumber > 0) {
                stringRedisTemplate.opsForValue().set("inventory001", String.valueOf(--inventoryNumber));
                retMessage = "成功卖出一个商品，库存剩余: " + inventoryNumber;
                System.out.println(retMessage);
            } else {
                retMessage = "商品卖完了，o(╥﹏╥)o";
            }
        }finally {
            stringRedisTemplate.delete(key);
        }
        return retMessage + "\t" + "服务端口号：" + port;
    }*/

    /**
     * V5.0 存在问题
     * 最后的判断+del不是一行原子命令操作 需要用lua脚本来进行修改
     */
    /*public String sale() {
        String retMessage = "";
        String key = "ddRedisLock";
        String uuidValue = IdUtil.simpleUUID() + ":" + Thread.currentThread().getId();

        // 改进点 加锁和过期时间设置必须在同一行 保证原子性
        while (!stringRedisTemplate.opsForValue().setIfAbsent(key, uuidValue, 30L, TimeUnit.SECONDS)) {
            try {
                TimeUnit.MILLISECONDS.sleep(20);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }

        //stringRedisTemplate.expire(key, 30L, TimeUnit.SECONDS);
        // 抢锁成功的请求线程 进行正常的业务逻辑 扣减库存
        try {
            //1 查询库存信息
            String result = stringRedisTemplate.opsForValue().get("inventory001");
            //2 判断库存是否足够
            Integer inventoryNumber = result == null ? 0 : Integer.parseInt(result);
            //3 扣减库存
            if (inventoryNumber > 0) {
                stringRedisTemplate.opsForValue().set("inventory001", String.valueOf(--inventoryNumber));
                retMessage = "成功卖出一个商品，库存剩余: " + inventoryNumber;
                System.out.println(retMessage);
            } else {
                retMessage = "商品卖完了，o(╥﹏╥)o";
            }
        }finally {
            // 改进点 只能删除自己的key 不能删除别人的key
            // v5.0判断加锁与解锁是不是同一个客户端，同一个才行，自己只能删除自己的锁，不误删他人的
            if (stringRedisTemplate.opsForValue().get(key).equalsIgnoreCase(uuidValue)) {
                stringRedisTemplate.delete(key);
            }
        }
        return retMessage + "\t" + "服务端口号：" + port;
    }*/

    /**
     * V6.0
     */
    /*public String sale() {
        String retMessage = "";
        String key = "ddRedisLock";
        String uuidValue = IdUtil.simpleUUID() + ":" + Thread.currentThread().getId();

        // 改进点 加锁和过期时间设置必须在同一行 保证原子性
        while (!stringRedisTemplate.opsForValue().setIfAbsent(key, uuidValue, 30L, TimeUnit.SECONDS)) {
            try {
                TimeUnit.MILLISECONDS.sleep(20);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }

        //stringRedisTemplate.expire(key, 30L, TimeUnit.SECONDS);
        // 抢锁成功的请求线程 进行正常的业务逻辑 扣减库存
        try {
            //1 查询库存信息
            String result = stringRedisTemplate.opsForValue().get("inventory001");
            //2 判断库存是否足够
            Integer inventoryNumber = result == null ? 0 : Integer.parseInt(result);
            //3 扣减库存
            if (inventoryNumber > 0) {
                stringRedisTemplate.opsForValue().set("inventory001", String.valueOf(--inventoryNumber));
                retMessage = "成功卖出一个商品，库存剩余: " + inventoryNumber;
                System.out.println(retMessage);
            } else {
                retMessage = "商品卖完了，o(╥﹏╥)o";
            }
        }finally {
            // 改进点 只能删除自己的key 不能删除别人的key
            // v5.0判断加锁与解锁是不是同一个客户端，同一个才行，自己只能删除自己的锁，不误删他人的
            if (stringRedisTemplate.opsForValue().get(key).equalsIgnoreCase(uuidValue)) {
                stringRedisTemplate.delete(key);
            }
        }
        return retMessage + "\t" + "服务端口号：" + port;
    }*/

    /**
     * V6.0 将判断+删除自己的合并为lua脚本保证原子性
     */
    /*public String sale() {
        String retMessage = "";
        String key = "ddRedisLock";
        String uuidValue = IdUtil.simpleUUID() + ":" + Thread.currentThread().getId();

        // 改进点 加锁和过期时间设置必须在同一行 保证原子性
        while (!stringRedisTemplate.opsForValue().setIfAbsent(key, uuidValue, 30L, TimeUnit.SECONDS)) {
            try {
                TimeUnit.MILLISECONDS.sleep(20);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }

        //stringRedisTemplate.expire(key, 30L, TimeUnit.SECONDS);
        // 抢锁成功的请求线程 进行正常的业务逻辑 扣减库存
        try {
            //1 查询库存信息
            String result = stringRedisTemplate.opsForValue().get("inventory001");
            //2 判断库存是否足够
            Integer inventoryNumber = result == null ? 0 : Integer.parseInt(result);
            //3 扣减库存
            if (inventoryNumber > 0) {
                stringRedisTemplate.opsForValue().set("inventory001", String.valueOf(--inventoryNumber));
                retMessage = "成功卖出一个商品，库存剩余: " + inventoryNumber;
                System.out.println(retMessage);
            } else {
                retMessage = "商品卖完了，o(╥﹏╥)o";
            }
        }finally {
            //V6.0 将判断+删除自己的合并为lua脚本保证原子性
            String luaScript =
                    "if (redis.call('get',KEYS[1]) == ARGV[1]) then " +
                            "return redis.call('del',KEYS[1]) " +
                            "else " +
                            "return 0 " +
                            "end";
            stringRedisTemplate.execute(new DefaultRedisScript<>(luaScript, Boolean.class), Arrays.asList(key), uuidValue);
        }
        return retMessage + "\t" + "服务端口号：" + port;
    }*/

    /**
     * V7.0 采用工厂模式+重写锁来实现分布式锁 达到可重入的效果
     */
    /*public String sale()
    {
        String retMessage = "";
        // 采用工厂模式获取对应的锁
        Lock redisLock = distributedLockFactory.getDistributedLock("redis");
        redisLock.lock();
        try
        {
            //1 查询库存信息
            String result = stringRedisTemplate.opsForValue().get("inventory001");
            //2 判断库存是否足够
            Integer inventoryNumber = result == null ? 0 : Integer.parseInt(result);
            //3 扣减库存
            if(inventoryNumber > 0) {
                stringRedisTemplate.opsForValue().set("inventory001",String.valueOf(--inventoryNumber));
                retMessage = "成功卖出一个商品，库存剩余: "+inventoryNumber+"\t";
                System.out.println(retMessage);
                testReEnter();
            }else{
                retMessage = "商品卖完了，o(╥﹏╥)o";
            }
        }catch (Exception e){
            e.printStackTrace();
        }finally {
            redisLock.unlock();
        }
        return retMessage+"\t"+"服务端口号："+port;
    }

    private void testReEnter()
    {
        Lock redisLock = distributedLockFactory.getDistributedLock("redis");
        redisLock.lock();
        try
        {
            System.out.println("==============测试可重入锁===============");
        }finally {
            redisLock.unlock();
        }
    }*/

    /**
     * V8.0  完成自动续期功能
     */
    public String sale()
    {
        String retMessage = "";
        // 采用工厂模式获取对应的锁
        Lock redisLock = distributedLockFactory.getDistributedLock("redis");
        redisLock.lock();
        try
        {
            //1 查询库存信息
            String result = stringRedisTemplate.opsForValue().get("inventory001");
            //2 判断库存是否足够
            Integer inventoryNumber = result == null ? 0 : Integer.parseInt(result);
            //3 扣减库存
            if(inventoryNumber > 0) {
                stringRedisTemplate.opsForValue().set("inventory001",String.valueOf(--inventoryNumber));
                retMessage = "成功卖出一个商品，库存剩余: "+inventoryNumber+"\t";
                System.out.println(retMessage);
                //暂停几秒钟线程,为了测试自动续期
                try { TimeUnit.SECONDS.sleep(120); } catch (InterruptedException e) { e.printStackTrace(); }
            }else{
                retMessage = "商品卖完了，o(╥﹏╥)o";
            }
        }catch (Exception e){
            e.printStackTrace();
        }finally {
            redisLock.unlock();
        }
        return retMessage+"\t"+"服务端口号："+port;
    }

    private void testReEnter()
    {
        Lock redisLock = distributedLockFactory.getDistributedLock("redis");
        redisLock.lock();
        try
        {
            System.out.println("==============测试可重入锁===============");
        }finally {
            redisLock.unlock();
        }
    }
}
