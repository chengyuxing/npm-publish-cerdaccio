# NPM离线内网仓库部署简化流程

## 前言

看此文档时，我认为你已经安装了**nodeJs**，离线仓库部署的目标服务器为Linux或Windows，假设Linux也安装了**nodeJs**。

如果目标服务器为Windows，推荐使用**Git Bash**作为终端来执行以下操作，保持和Linux环境命令一致。

如果**nodeJs**版本太老，推荐升级到较新版本。

终端执行：

```bash
$ npm -v
8.19.2
$ node -v
v18.11.0
```

## 准备

1. 新建文件夹 `npm_deps` 并进入，使用npm新建一个**初始化项目**来准备一些必要的包：

   ```bash
   $ npm init
   ```

   一路回车，编辑 `package.json` 文件，以下依赖至少包含 **verdaccio** ：

   ```json
   {
     "name": "npm_deps",
     "version": "1.0.0",
     "description": "",
     "main": "index.js",
     "scripts": {
       "test": "echo \"Error: no test specified\" && exit 1"
     },
     "author": "",
     "license": "ISC",
     "dependencies": {
       "@types/node": "^20.10.5",
       "node-fetch": "^2.6.7",
       "npm": "^10.2.5",
       "nrm": "^1.2.6",
       "pm2": "^5.3.0",
       "pnpm": "^8.12.1",
       "verdaccio": "^5.29.0",
       "yarn": "^1.22.21"
     }
   }
   ```

   安装依赖执行：

   ```bash
   $ npm install
   ```

2. 互联网环境下安装 `verdaccio`

   ```bash
   $ npm install -g verdaccio
   $ verdaccio -v
   v5.15.4
   ```

   安装完成后，本地配置文件默认在：`~/.config/verdaccio/config.yaml` 目录。

3. 修改配置文件（迁移内外后再修改也可以）

   - 注释以下节点：

     ```yaml
     uplinks:
      #  npmjs:
      #    url: https://registry.npmjs.org/
     ```

     > 说明一下：这里最好将其注释，因为要将npmjs仓库的包发布缓存到本地，为避免与npmjs官方相冲突，为了流程顺利，需要将其注释。

   - 修改请求上传包大小：

     ```yaml
     max_body_size: 500mb
     ```

     > 建议修改稍微大一点，默认是10mb，有些包超过10mb，例如typescript就无法发布；

4. 拷贝 `verdaccio` 主程序整个文件夹：具体在你默认的**npm**目录下的**node_modules**文件夹，可参考如下：

   - Windows：`~/AppData/Roaming/npm/node_modules`；
   - macOS：`/opt/homebrew/lib/node_modules` （前提是使用HomeBrew安装的nodeJs）

5. 拷贝配置文件 `~/.config/verdaccio` 整个文件夹 `verdaccio`；

6. 拷贝**初始化项目**及其 `node_modules`。

## 内网配置

1. 配置文件同样的放置到用户目录的 `.config` 下；

2. **verdaccio** 主程序先放到任意目录下。

3. 通过**Git Bash**进入到 **verdaccio** 主程序目录并执行：

   ```bash
   $ ./bin/verdaccio
   ```

   > 如果正常启动，则现在可以访问 [localhost](http://localhost:4873) 进入页面。

## 批量推送

1. 修改本地npm指向地址：

   ```bash
   $ npm set registry http://localhost:4873/
   ```

2. 注册用户：

   ```bash
   $ npm adduser --registry http://localhost:4873/
   ```

3. 批量推送：

   ```bash
   $ java -jar npm-batch-publish.jar <npm_deps/node_modules>
   ```

   > 例如：`java -jar npm-batch-publish.jar ~/npm_deps/node_modules`

4. 全局安装**verdaccio**：

   ```bash
   $ npm install -g verdaccio
   ```

   如果安装报错例如：`npm ERR! notarget No matching version found for node-fetch@cjs.` 

   进入到你拷贝过来的初始化项目 `node_modules/node-fetch/`，修改 `package.json` 的 `version` ，随便换一个版本号，例如 `2.10.0` 并在当前文件夹下执行：

   ```bash
   $ npm publish --tag cjs
   ```

   重新安装 `verdaccio` 即可成功。

5. 停止临时拷贝过来的**verdaccio**并可选择删除；

6. 启用全局的**verdaccio**

   ```bash
   $ verdaccio
   ```

   > 如果成功启动说明安装配置正确，现在可以`Ctrl + c` 停止运行。

7. 使用**pm2**来管理驻留后台运行

   ```bash
   $ npm install -g pm2
   $ pm2 start verdaccio
   ```

   :warning: Windows下如果报错：

   ```
   SyntaxError: Invalid or unexpected token
   ...
   @ECHO off
   ^
   ```

   指定到具体的**verdaccio**执行如下：

   ```bash
   $ pm2 start ~/AppData/Roaming/npm/node_modules/verdaccio/bin/verdaccio
   ```

## 客户端配置

执行：`npm set registry http:<服务器主机>:4873`

至此已可以通过内网私有仓库创建webpack项目。

## 附录

其他需要的互联网的所有包则可通过以下步骤重复执行：

1. 新建npm项目，`dependencies` 节点添加所需依赖；
2. 执行：`npm install`；
3. 拷贝到内网；
4. 执行批量推送。