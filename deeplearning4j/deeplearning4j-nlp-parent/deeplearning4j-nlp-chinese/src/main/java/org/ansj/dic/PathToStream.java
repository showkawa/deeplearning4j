package org.ansj.dic;

import org.ansj.dic.impl.File2Stream;
import org.ansj.dic.impl.Jar2Stream;
import org.ansj.dic.impl.Jdbc2Stream;
import org.ansj.dic.impl.Url2Stream;
import org.ansj.exception.LibraryException;
import org.deeplearning4j.common.config.DL4JClassLoading;

import java.io.InputStream;

/**
 * 将路径转换为流，如果你需要实现自己的加载器请实现这个类，使用这个类可能需要自己依赖第三方包，比如jdbc连接和nutz
 * 
 * @author ansj
 *
 */
public abstract class PathToStream {

    public static InputStream stream(String path) {
        try {
            if (path.startsWith("file://")) {
                return new File2Stream().toStream(path);
            } else if (path.startsWith("jdbc://")) {
                return new Jdbc2Stream().toStream(path);
            } else if (path.startsWith("jar://")) {
                return new Jar2Stream().toStream(path);
            } else if (path.startsWith("class://")) {
                // Probably unused
                return loadClass(path);
            } else if (path.startsWith("http://") || path.startsWith("https://")) {
                return new Url2Stream().toStream(path);
            } else {
                return new File2Stream().toStream(path);
            }
        } catch (Exception e) {
            throw new LibraryException(e);
        }
    }

    public abstract InputStream toStream(String path);

    static InputStream loadClass(String path) {
        String className = path
                .substring("class://".length())
                .split("\\|")[0];

        return DL4JClassLoading
                .createNewInstance(className, PathToStream.class)
                .toStream(path);
    }
}
