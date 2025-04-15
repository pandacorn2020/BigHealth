package com.bighealth.service;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/*
西医循证：EBM
中成药：CPM
中医辩证：TCM
营养医学：NUTR
免疫健康：IMM
器官功能：ORGAN
衰老与退化：AGING
 */
public class Schemas {
    public static final String EBM = "EBM";
    public static final String CPM = "CPM";
    public static final String TCM = "TCM";
    public static final String NUTR = "NUTR";
    public static final String IMM = "IMM";
    public static final String ORGAN = "ORGAN";
    public static final String AGING = "AGING";
    public static final String[] SCHEMAS = {EBM, CPM, TCM, NUTR, IMM, ORGAN, AGING};
    public static final Map<String, String> SCHEMA_DIR_MAP = new HashMap<>();

    public static final Map<String, String> SCHEMA_DESCRIPTION_MAP = new HashMap<>();

    static {
        SCHEMA_DIR_MAP.put(EBM, "指南共识篇");
        SCHEMA_DIR_MAP.put(CPM, "中成药篇");
        SCHEMA_DIR_MAP.put(TCM, "中医基础篇");
        SCHEMA_DIR_MAP.put(NUTR, "营养医学篇");
        SCHEMA_DIR_MAP.put(IMM, "免疫健康篇");
        SCHEMA_DIR_MAP.put(ORGAN, "器官篇");
        SCHEMA_DIR_MAP.put(AGING, "抗衰老篇");

        SCHEMA_DESCRIPTION_MAP.put(EBM, "西医");
        SCHEMA_DESCRIPTION_MAP.put(CPM, "中成药");
        SCHEMA_DESCRIPTION_MAP.put(TCM, "中医");
        SCHEMA_DESCRIPTION_MAP.put(NUTR, "营养");
        SCHEMA_DESCRIPTION_MAP.put(IMM, "免疫健康");
        SCHEMA_DESCRIPTION_MAP.put(ORGAN, "器官");
        SCHEMA_DESCRIPTION_MAP.put(AGING, "抗衰老");
    }

    public static String getSchemaDir(String schema) {
        return SCHEMA_DIR_MAP.get(schema);
    }

    public static String getSchemaDescription(String schema) {
        return SCHEMA_DESCRIPTION_MAP.get(schema);
    }

    public static Collection<String> allSchemas() {
        return Collections.unmodifiableCollection(SCHEMA_DIR_MAP.keySet());
    }

    /**
     * （1）西医循证及就医指导；
     * （2）中医辩证及调理建议；
     * （3）营养健康分析和调理建议；
     * （4）免疫健康分析和调理建议；…。…。
     *
     * 二层菜单应该是：
     * 中医辩证
     * 免疫健康
     * 营养健康
     * 器官功能
     * 衰老与退化
     * @return
     */
    public static String[] primarySchemas() {
        return new String[] {EBM, TCM, NUTR, IMM, CPM};
    }

    public static String[] secondarySchemas() {
        return new String[] {TCM, IMM, NUTR, ORGAN, AGING};
    }


}
