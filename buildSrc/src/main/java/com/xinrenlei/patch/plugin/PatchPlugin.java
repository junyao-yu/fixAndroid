package com.xinrenlei.patch.plugin;

import com.android.build.gradle.AppExtension;
import com.android.build.gradle.AppPlugin;
import com.android.build.gradle.api.ApplicationVariant;
import com.android.utils.FileUtils;

import org.apache.commons.compress.utils.IOUtils;
import org.gradle.api.Action;
import org.gradle.api.GradleException;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.tasks.TaskOutputs;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import java.util.regex.Matcher;


/**
 * Auth：yujunyao
 * Since: 2020/12/29 6:51 PM
 * Email：yujunyao@xinrenlei.net
 */

public class PatchPlugin implements Plugin<Project> {


    @Override
    public void apply(Project project) {
        System.out.println("======com.xinrenlei.patch.plugin.PatchPlugin=========");

        if (!project.getPlugins().hasPlugin(AppPlugin.class)) {
            throw new GradleException("无法在非Android application 插件中使用热修复插件");
        }

        //创建一个patch配置，就和引入com.android.application 配置 android{}一样
        project.getExtensions().create("patch", PatcheExtension.class);

        //gradle执行会解析build.gradle文件，afterEvaluate表示在解析完成之后再执行我们的代码
        project.afterEvaluate(new Action<Project>() {
            @Override
            public void execute(Project project) {
                PatcheExtension patcheExtension = project.getExtensions().findByType(PatcheExtension.class);

                //获取用户的配置，在debug模式下是否开启热修复
                final boolean debugOn = patcheExtension.isDebugOn();

                //android配置
                AppExtension appExtension = project.getExtensions().getByType(AppExtension.class);

                //android项目默认会有debug  release
                //那么getApplicationVariants就是debug releas的集合
                appExtension.getApplicationVariants().all(new Action<ApplicationVariant>() {
                    @Override
                    public void execute(ApplicationVariant applicationVariant) {

                        if (applicationVariant.getName().contains("debug") && !debugOn) {
                            //如果是debug模式，并且设置了debug模式下不开启热修复，直接返回
                            return;
                        }


                        configTasks(project, applicationVariant, patcheExtension);
                    }
                });
            }
        });
    }

    private void configTasks(final Project project, final ApplicationVariant variant, final PatcheExtension patchExtension) {
        //获得debug/release
        String variantName = variant.getName();
        //首字母大写
        String capitalizeName = Utils.capitalize(variantName);

        //热修复的输出目录
        File outputDir;
        //如果没有指名输出目录，默认输出到 build/patch/debug(release) 下
        if (!Utils.isEmpty(patchExtension.getOutput())) {
            outputDir = new File(patchExtension.getOutput(), variantName);
        } else {
            outputDir = new File(project.getBuildDir(), "patch/" + variantName);
        }
        outputDir.mkdirs();

        //获得android的混淆任务 -- 不同的gradle版本，编译的时候任务名有可能是不一样的
        final Task proguardTask =
                project.getTasks().findByName("transformClassesAndResourcesWithProguardFor" + capitalizeName);

        /**
         * 备份本次的mapping文件
         */
        final File mappingBak = new File(outputDir, "mapping.txt");
        //如果没开启混淆，则为null，不需要备份mapping
        if (proguardTask != null) {
            // dolast：在这个任务之后再干一些事情
            // 在混淆后备份mapping
            proguardTask.doLast(new Action<Task>() {
                @Override
                public void execute(Task task) {
                    //混淆任务输出的所有文件
                    TaskOutputs outputs = proguardTask.getOutputs();
                    Set<File> files = outputs.getFiles().getFiles();
                    for (File file : files) {
                        //把mapping文件备份
                        if (file.getName().endsWith("mapping.txt")) {
                            try {
                                FileUtils.copyFile(file, mappingBak);
                                project.getLogger().error("mapping: " + mappingBak.getCanonicalPath());
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                            break;
                        }
                    }
                }
            });
        }
        //将上次混淆的mapping应用到本次,如果没有上次的混淆文件就没操作
        Utils.applyMapping(proguardTask, mappingBak);

        /**
         * 在混淆后 记录类的hash值，并生成补丁包
         */
        final File hexFile = new File(outputDir, "hex.txt");

        // 需要打包补丁的类的jar包
        final File patchClassFile = new File(outputDir, "patchClass.jar");
        // 用dx打包后的jar包:补丁包
        final File patchFile = new File(outputDir, "patch.jar");


        //打包dex任务
        final Task dexTask =
                project.getTasks().findByName("transformClassesWithDexBuilderFor" + capitalizeName);

        //dofirst：在任务之前干一些事情
        // 在把class打包dex之前，插桩并记录每个class的md5 hash值
        dexTask.doFirst(new Action<Task>() {
            @Override
            public void execute(Task task) {

                /**
                 *  插桩 记录md5并对比
                 */
                PatchGenerator patchGenerator = new PatchGenerator(project, patchFile,
                        patchClassFile, hexFile);
                //用户配置的application，实际上可以解析manifest自动获取，但是java实现太麻烦了，干脆让用户自己配置
                String applicationName = patchExtension.getApplicationName();
                //windows下 目录输出是  xx\xx\  ,linux下是  /xx/xx ,把 . 替换成平台相关的斜杠
                applicationName = applicationName.replaceAll("\\.",
                        Matcher.quoteReplacement(File.separator));
                //记录类的md5
                Map<String, String> newHexs = new HashMap<>();
                //任务的输入，dex打包任务要输入什么？ 自然是所有的class与jar包了！
                Set<File> files = dexTask.getInputs().getFiles().getFiles();
                for (File file : files) {
                    String filePath = file.getAbsolutePath();
                    if (filePath.endsWith(".jar")) {
                        processJar(applicationName, file, newHexs, patchGenerator);

                    } else if (filePath.endsWith(".class")) {
                        processClass(applicationName, variant.getDirName(), file, newHexs,
                                patchGenerator);
                    }

                }
                //类的md5集合 写入到文件
                Utils.writeHex(newHexs, hexFile);
                try {
                    //生成补丁
                    patchGenerator.generate();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });

        Task task = project.getTasks().create("patch" + capitalizeName);
        task.setGroup("patch");
        task.dependsOn(dexTask);
    }

    void processClass(String applicationName, String dirName, File file,
                      Map<String, String> hexs,
                      PatchGenerator patchGenerator) {
        String filePath = file.getAbsolutePath();
        //注意这里的filePath包含了目录+包名+类名，所以去掉目录
        String className = filePath.split(dirName)[1].substring(1);
        //application或者android support我们不管
        if (className.startsWith(applicationName) || Utils.isAndroidClass(className)) {
            return;
        }
        try {
            FileInputStream is = new FileInputStream(filePath);
            //执行插桩
            byte[] byteCode = ClassUtils.referHackWhenInit(is);
            String hex = Utils.hex(byteCode);
            is.close();

            FileOutputStream os = new FileOutputStream(filePath);
            os.write(byteCode);
            os.close();

            hexs.put(className, hex);
            //对比缓存的md5，不一致则放入补丁
            patchGenerator.checkClass(className, hex, byteCode);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    void processJar(String applicationName, File file, Map<String, String> hexs,
                    PatchGenerator patchGenerator) {
        try {
            //  无论是windows还是linux jar包都是 /
            applicationName = applicationName.replaceAll(Matcher.quoteReplacement(File.separator),
                    "/");
            File bakJar = new File(file.getParent(), file.getName() + ".bak");
            JarOutputStream jarOutputStream = new JarOutputStream(new FileOutputStream(bakJar));

            JarFile jarFile = new JarFile(file);
            Enumeration<JarEntry> entries = jarFile.entries();
            while (entries.hasMoreElements()) {
                JarEntry jarEntry = entries.nextElement();

                jarOutputStream.putNextEntry(new JarEntry(jarEntry.getName()));
                InputStream is = jarFile.getInputStream(jarEntry);

                String className = jarEntry.getName();
                if (className.endsWith(".class") && !className.startsWith(applicationName)
                        && !Utils.isAndroidClass(className) && !className.startsWith("com/xinrenlei" +
                        "/patch")) {

                    byte[] byteCode = ClassUtils.referHackWhenInit(is);
                    String hex = Utils.hex(byteCode);
                    hexs.put(className, hex);
                    //对比缓存的md5，不一致则放入补丁
                    patchGenerator.checkClass(className, hex, byteCode);
                    jarOutputStream.write(byteCode);
                } else {
                    //输出到临时文件
                    jarOutputStream.write(IOUtils.toByteArray(is));
                }
                is.close();
                jarOutputStream.closeEntry();
            }
            jarOutputStream.close();
            jarFile.close();
            file.delete();
            bakJar.renameTo(file);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
