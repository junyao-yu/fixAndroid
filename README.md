# fixAndroid
通过tinker，实现简易版的热修复，主要是了解其原理

功能：


1、通过dx打补丁修复


2、解决AndroidN的通过JIT编译一些代码后保存到profile文件，带设备空闲的时候AOT生成app_image的base.art文件，在app启动时自动加载倒是补丁包无效的问题。


3、解决Dalvik虚拟机CLASS_ISPREVERIFIED校验问题


4、字节码插桩技术


5、gradle插件
