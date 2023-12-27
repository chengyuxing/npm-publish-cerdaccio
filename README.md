# NPM批量发布包小程序

将项目 `node_modules` 下所有公有库的依赖批量重新发布到本地私有仓库，实际意义主要在于解决内网离线环境下难以搭建前端开发环境的问题：

大致流程可参考[文档](NPM_OFFLINE_CONFIG.md)，具体步骤参考[文档](NPM_OFFLINE_DEPLOY.md)。

## 例子

```shell
$ java -jar npm-publish-app.jar "/Users/chengyuxing/Downloads/vue-demo/node_modules" "http://192.168.1.103:4873"
```

默认执行 `npm publish`，如果指定了第二个参数，将推送到指定的地址如：

```shell
$ npm publish --registry http://192.168.1.103:4873
```
