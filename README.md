# NPM批量发布包小程序

将项目 `node_modules` 下所有公有库的依赖批量重新发布到本地私有仓库，实际意义主要在于解决内网离线环境下难以搭建前端开发环境的问题：
具体流程可以归纳为：

1. 新建一个webpack项目，如 `angular` ，执行 `npm install`；
2. 本地安装npm私有仓库，例如：`verdaccio`；
3. 运行私有仓库；
4. 执行本批量发布包小程序发布依赖到本地仓库；
5. 拷贝缓存到服务器上；
6. 服务器安装：`verdaccio`；
7. 配置并替换缓存；
8. 启动完成。

具体流程可参考[文档](NPM_OFFLINE_CONFIG.md)

## 例子

```shell
$ java -jar npm-publish-app.jar "/Users/chengyuxing/Downloads/vue-demo/node_modules" "http://192.168.1.103:4873"
```

默认执行 `npm publish`，如果指定了第二个参数，将推送到指定的地址如：

```shell
$ npm publish --registry http://192.168.1.103:4873
```
