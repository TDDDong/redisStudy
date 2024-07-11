package com.dd.redis7.service;

import com.dd.redis7.entities.Customer;
import com.dd.redis7.mapper.CustomerMapper;
import com.dd.redis7.utils.CheckUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

@Service
@Slf4j
public class CustomerService {
    public static final String CACHE_KEY_CUSTOMER = "customer:";

    @Resource
    private CustomerMapper customerMapper;
    @Resource
    private RedisTemplate redisTemplate;

    public void addCustomer(Customer customer) {
        int i = customerMapper.insertSelective(customer);

        if (i > 0) {
            //mysql插入成功 需要重新查询一次再插入redis中
            Customer result = customerMapper.selectByPrimaryKey(customer.getId());

            String key = CACHE_KEY_CUSTOMER + customer.getId();

            redisTemplate.opsForValue().set(key, result);
        }
    }

    public Customer findCustomerById(Integer customerId) {
        Customer customer = null;

        String key = CACHE_KEY_CUSTOMER + customerId;
        // 先去redis查询
        customer = (Customer) redisTemplate.opsForValue().get(key);

        if (customer == null) {
            customer = customerMapper.selectByPrimaryKey(customerId);
            if (customer != null) {
                redisTemplate.opsForValue().set(key, customer);
            }
        }
        return customer;
    }

    @Resource
    private CheckUtils checkUtils;
    public Customer findCustomerByIdWithBloomFilter (Integer customerId)
    {
        Customer customer = null;

        //缓存key的名称
        String key = CACHE_KEY_CUSTOMER + customerId;

        //布隆过滤器check，无是绝对无，有是可能有
        //===============================================
        if(!checkUtils.checkWithBloomFilter("whitelistCustomer",key))
        {
            log.info("白名单无此顾客信息:{}",key);
            return null;
        }
        //===============================================

        //1 查询redis
        customer = (Customer) redisTemplate.opsForValue().get(key);
        //redis无，进一步查询mysql
        if (customer == null) {
            //2 从mysql查出来customer
            customer = customerMapper.selectByPrimaryKey(customerId);
            // mysql有，redis无
            if (customer != null) {
                //3 把mysql捞到的数据写入redis，方便下次查询能redis命中。
                redisTemplate.opsForValue().set(key, customer);
            }
        }
        return customer;
    }
}
