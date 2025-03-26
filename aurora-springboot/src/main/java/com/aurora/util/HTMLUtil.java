package com.aurora.util;

import com.github.houbb.sensitive.word.bs.SensitiveWordBs;

/**
 * 通过使用正则表达式实现对 HTML 标签和属性的过滤，确保更安全和干净的 HTML 内容。
 */
public class HTMLUtil {

    // temporary unused
    private static final SensitiveWordBs sensitiveWordBs = SensitiveWordBs.newInstance()
            .ignoreCase(true)
            .ignoreWidth(true)
            .ignoreNumStyle(true)
            .ignoreChineseStyle(true)
            .ignoreEnglishStyle(true)
            .ignoreRepeat(true)
            .enableNumCheck(false)
            .enableEmailCheck(false)
            .enableUrlCheck(false)
            .init();

    /**
     * 过滤掉 HTML 标签（除了 <img> 标签外）。
     * 删除事件处理属性，如 onload 和 onerror。
     * 最后调用 deleteHTMLTag 方法去除更多标签和字符实体。
     * @param source
     * @return
     */
    public static String filter(String source) {
        source = source.replaceAll("(?!<(img).*?>)<.*?>", "")
                .replaceAll("(onload(.*?)=)", "")
                .replaceAll("(onerror(.*?)=)", "");
        return deleteHMTLTag(source);
    }

    /**
     * 删除 HTML 实体字符，如 &lt;, &gt; 等。
     * 删除 <script> 和 <style> 标签及其内容。
     * @param source
     * @return
     */
    public static String deleteHMTLTag(String source) {
        source = source.replaceAll("&.{2,6}?;", "");
        source = source.replaceAll("<[\\s]*?script[^>]*?>[\\s\\S]*?<[\\s]*?\\/[\\s]*?script[\\s]*?>", "");
        source = source.replaceAll("<[\\s]*?style[^>]*?>[\\s\\S]*?<[\\s]*?\\/[\\s]*?style[\\s]*?>", "");
        return source;
    }

}
