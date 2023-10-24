# hm-dianping
chuanzhi redis practise program
> 是一个用于强化redis的java点评项目。
主要功能有
### 短信登录
基于session实现登录功能
![WcnS3PbgiJzdBI1.png](https://s2.loli.net/2023/10/24/WcnS3PbgiJzdBI1.png)
使用redis作为缓存，并考虑了``缓存穿透``，``缓存雪崩``，``缓存击穿``情况和策略。
### 优惠券秒杀
![NOnGdWXa3lvgR1j.png](https://s2.loli.net/2023/10/24/NOnGdWXa3lvgR1j.png)
用redis生成全局唯一ID
同时考虑了集群模式下的安全问题，使用redisson结合lua脚本解决。
使用redis实现了消息队列异步下单，优化秒杀业务的性能
### 探店功能
主要包括发布笔记，查看笔记和点赞功能

### 好友关注
有关注、取关、共同关注和关注推送功能，使用feed流实现推送功能

### 附件商户
利用redis的 Geo功能实现

### 签到和统计
签到是用bitMap实现的，统计暂时未实现

### 前置工作
由于我是在Oracle服务器上使用的redis，需要配置远程访问，密码等
````
bind 0.0.0.0  # 让外网可以访问
requirepass --- # 设置redis密码
port 6550 # 更改redis端口
````
