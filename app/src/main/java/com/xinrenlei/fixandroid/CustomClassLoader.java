package com.xinrenlei.fixandroid;

import dalvik.system.PathClassLoader;

/**
 * Auth：yujunyao
 * Since: 2020/12/28 11:30 AM
 * Email：yujunyao@xinrenlei.net
 */

public class CustomClassLoader extends PathClassLoader {

    public CustomClassLoader(String dexPath, String librarySearchPath, ClassLoader parent) {
        super(dexPath, librarySearchPath, parent);
    }

    @Override
    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        return super.loadClass(name, resolve);
    }

}
