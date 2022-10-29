# NPM内网离线仓库安装与配置

## 前言

看此文档时，我认为你已经安装了**nodeJs(版本>=12)**，并且离线仓库部署的目标服务器为Linux，假设Linux也安装了nodeJs，同样的版本 **>= 12**。

以我本地环境（**MACOS 13**）与目标服务器（**CentOS 7**）为例，在终端分别能执行：

```bash
$ npm -v
8.19.2
$ node -v
v18.11.0
```

## 互联网环境安装npm私有仓库管理器

```bash
$ npm install -g verdaccio
$ verdaccio -v
v5.15.4
```

安装完成后，本地配置文件默认在：`~/.config/verdaccio/config.yaml` 目录。

编辑此配置文件，其中首先需要关注的几个地方：

- 默认的本地包缓存目录：`storage: /Users/chengyuxing/.local/share/verdaccio/storage`；

- ```yaml
  uplinks:
   #  npmjs:
   #    url: https://registry.npmjs.org/
  ```

  > 说明一下：这里最好将其注释，因为要将npmjs仓库的包发布缓存到本地，为避免与npmjs官方相冲突，为了流程顺利，需要将其注释。

- ```yaml
  max_body_size: 500mb
  ```

  > 建议修改稍微大一点，默认是10mb，有些包超过10mb，例如typescript就无法发布；


## 缓存需要的node_modules

1. 新建一个webpack项目，例如angular、vue等;
2. 将自己需要的所有包添加到 `dependencies` 中;
3. 进入项目，执行 `npm install`，确保不报错；
4. 执行完毕，可看到当前项目中 `node_modules` 文件夹。

## 批量发布缓存node_modules流程

**启动服务**：

```bash
$ verdaccio
```

将当前npm服务地址指向本地：

```bash
$ npm set registry http://localhost:4873
```

**注册用户，根据提示按顺序输入用户名、密码、邮箱：**

```bash
$ npm adduser --registry http://localhost:4873
```

**运行批量发布包工具**：

```bash
$ java -jar npm-publish-app.jar "/Users/chengyuxing/WebstormProjects/ng14-starter/node_modules"
```

**执行完毕后**：

1. 访问地址：`http://localhost:4873`，可以看到已经将整个 `node_modules` 发布到了本地仓库；
2. 此时断网的情况下，新建项目；
3. 执行：`npm install` ，可成功下载所有依赖项；
4. 运行成功。

## 本地启用远程访问

编辑 `~/.config/verdaccio/config.yaml` 文件：

```yaml
listen: 0.0.0.0:4873
```

将本地仓库指向地址：

```bash
npm set registry http://127.0.0.1:4873
```

重启 `verdaccio` 服务，此时可以通过**局域网IP**来进行访问。

## 可选[推荐]

安装 `pm2`管理进程：

```bash
$ npm install -g pm2
```

使用 `pm2` 启动 `verdaccio`。

## 迁移内网

### 拷贝必要文件

1. `~/.config/`**`verdaccio`**；

   > `config.yaml` 所在文件夹整个拷贝。

2. `.../verdaccio/`**`storage`**；

3. 全局 `node_modules` 下安装的 **`verdaccio`**安装文件夹；

> 拷贝黑体字加粗部分即可。

### 内网配置

1. 下载nodeJs linux版，版本最好满足 `verdaccio` 的最低要求；

2. 解压到目录，例如：`/usr/local/node`；

3. 确保：`./bin/node -v` 和`./bin/npm -v` 可以执行输出版本号；

4. 创建软连接：

   ```bash
   $ ln -s /usr/local/node/bin/node /usr/local/bin/node
   $ ln -s /usr/local/node/bin/npm /usr/local/bin/npm
   ```

5. 将 `~/.config/`**`verdaccio`** 拷贝到linux系统下的此目录：`~/.config`；

6. 将 **storage** 拷贝到喜欢的目录；

7. 编辑 `~/.config/verdaccio/config.yaml`：修改**storage**节点为喜欢的目录，并保存。

8. 将 `verdaccio` 安装文件夹拷贝到：`/usr/local/node/lib/node_modules/` 下，并创建软连接：

   ```bash
   $ ln -s /usr/local/node/lib/node_modules/verdaccio/bin/verdaccio /usr/local/bin/verdaccio
   ```

9. 配置仓库地址为本机地址：`npm set registry http://....4873/`；

10. 启动：`verdaccio`；

11. 执行：`npm install -g pm2`；

    > 前提是 **storage** 里已有 pm2 ;

12. 创建**pm2**软连接：

    ```bash
    $ ln -s /usr/local/node/lib/node_modules/pm2/pm2 /usr/local/bin/pm2
    ```

13. `Ctrl+c` 停止**verdaccio**服务；

14. 使用**pm2**启动**verdaccio**服务：

    ```bash
    $ pm2 start verdaccio
    ```

15. 客户端 **npm** 指定仓库地址：

    ```bash
    $ npm set registry http://服务器地址:4873
    ```

16. 至此已可以通过内网私有仓库创建webpack项目。

## 推荐的必要系统级依赖

`packagee.json`

```json
"devDependencies": {
    "pnpm": "^7.14.0",
    "npm": "8.19.2",
    "yarn": "1.22.19",
    "nrm": "1.2.5",
    "pm2": "5.2.2",
    "@types/node": "18.11.7",
    "verdaccio": "5.15.4"
  }
```

## 附录

官方地址：https://registry.npmjs.org/

本机地址：http://localhost:4873

账号：admin

密码：123456

邮箱：cheng...o@gmail.com
