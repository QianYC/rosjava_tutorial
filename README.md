# ROS Android开发
目前想在安卓上进行ROS开发还是比较蛋疼的（可能有其他库我没发现），我是用[rosjava/android_core](https://github.com/rosjava/android_core)
进行开发的。然而这个库文档写得乱的一笔，网上的博客质量也不高，我这一路摸着石头走过来也是十分的酸爽，因此想着把我的经历总结一下给后人留个参照。

**以下步骤仅为个人经历，可能会走弯路，但是可行！！！**

- 安装rosjava。官方提供了源代码安装、deb安装和非ROS安装这三个选项，我选了前两个中的某一个。关于初始化workspace，我是遵从官方的指导新建了
~/rosjava/src文件夹，没有直接用我ROS的workspace，因为我怕装呲了把我环境搞坏……

- 新建rosjava项目。rosjava提供了如下命令：catkin_create_android_library_project、catkin_create_rosjava_library_project、
catkin_create_android_pkg、catkin_create_rosjava_pkg、catkin_create_android_project、catkin_create_rosjava_project。
现在回想起来这些命令应该都能用，但我当时选择了最蠢的办法：直接把android_core项目clone下来，然后在下面建子项目hhhh……

- 配置项目。android_core项目下有很多子项目，其中只有android_core_components是必要的，其他都是示例。我们可以照着示例项目配置
新建项目的依赖。在新项目的build.gradle中最关键的内容是：

```
dependencies {
    compile project(':android_core_components')
}
```

这表明你的项目的构建依赖android_core_components。为了避免将来会出现的问题，再加上：

```
configurations {
        all*.exclude group: 'com.google.guava', module: 'listenablefuture'
    }
```

然后在settings.gradle中有样学样地加上：

```
include "项目名"
```

如果你的新项目名不是以"android_"开头的话，对父项目android_core的build.gradle作如下修改：把第32行的内容改为

```
configure(subprojects.findAll { true })
```

至此如果你的网络没有啥问题的话，你的新项目是应该能build的。（我没翻墙，AS也没开proxy）

# rosjava自定义message
这同样是一个漫长又蛋疼的摸索过程，不过最后也确实成功了……这次的经历给我一种“rosjava官方和社区是不是死了”的感觉，在ROS下进行安卓开发属实麻烦。
如果能重来，我选择非ROS安装……或者另寻他路。

- 在c++项目中自定义message。因为我是要让安卓程序和c++程序互相通信，所以才有这一步，没这需求的可以跳过。**ROS Robot Programming**
这本书讲得很清楚了，在c++中自定义message无无非就是添加message_generation依赖，在相应文件夹下添加message文件，配置CMakeLists，然后
执行catkin_make，就会自动生成message对应的头文件，在项目中引用即可。如果使用ide比如CLion编译项目的话，ide可能会找不到这些自动生成的头文件，
那是因为这些头文件都是在{your ros workspace}/devel/include/{project name}下，而ide并不知道这个位置。除此之外，ide是通过cmake来编译项目的，
而非catkin_make命令，因此也不会自动生成头文件。你只需要把自动生成的头文件复制到ide编译输出的对应位置即可。（对于CLion来说，这个位置是
{project source dir}/cmake-build-debug/devel/include/{project name}）

- 利用[rosjava_messages](https://github.com/rosjava/rosjava_messages)生成message对应的lib。clone这个项目到你的rosjava workspace，
同时新建一个项目：

```
catkin_create_pkg {project name} message_generation
```

我把这个项目就建在了rosjava的workspace，虽然我事后深深怀疑这个项目完全可以作为rosjava_messages的子项目。
**请注意我们新建的项目并不是java项目，而是普通的cmake项目！**接下来依旧是三板斧：在对应文件夹下添加message文件，修改CMakeLists，修改package.xml。
到此为止，我们还不能生成message对应的Java类，接下来就是重点：在rosjava_messages项目的CMakeLists中的generate_rosjava_messages指令
中添加新建的项目名，在package.xml中增加一个<build_depend>标签，内容也是新建的项目名。然后回到workspace，
指定catkin_make编译rosjava_messages项目（不指定也行）：

```
catkin_make --pkg rosjava_messages
```

生成的jar包在{rosjava workspace}/devel/share/maven/org/ros/rosjava_messages/{project name}/{version}。jar包的版本号在
项目自己的package.xml中定义。

- 把jar包暴露给maven。为了让你的rosjava项目能够使用你刚刚自定义的message，你需要告诉maven你的jar包在哪里。一种方法：把jar包连同
他的目录结构一起拷贝至你的本地maven库，然后在你rosjava项目的buildscript.gradle的repositories中加上：

```
mavenLocal()
```

第二种方法：直接在rosjava项目的buildscript.gradle的repositories中加上：

```
maven {
        url "file://{rosjava workspace absolute path}/devel/share/maven"
      }
```