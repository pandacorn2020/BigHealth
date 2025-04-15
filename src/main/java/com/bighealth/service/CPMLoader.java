package com.bighealth.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class CPMLoader {

    @Autowired
    private DocumentLoader documentLoader;

    private static final String SYMBOL = "（";
    private static final String ESYMBOL = "(";
    public void collectSlices(List<String> nameList, List<String> sliceList) throws Exception {
        List<String> allLines = documentLoader.readAllLines(
                "中成药篇/新编国家中成药第3版.txt");
        Set<String> nameSet = processDirectory();
        StringJoiner joiner = new StringJoiner("\n");
        String medicineName = null;

        for (String l : allLines) {
            String tok = l;
            if (tok.contains(SYMBOL)) {
                int index = tok.indexOf(SYMBOL);
                tok = tok.substring(0, index);
            } else if (tok.contains(ESYMBOL)) {
                int index = tok.indexOf(ESYMBOL);
                tok = tok.substring(0, index);
            }
            if (nameSet.contains(tok)) {
                // new medicine begins
                if (medicineName != null) {
                    nameList.add(medicineName);
                    sliceList.add(joiner.toString());
                }
                medicineName = tok;
                joiner = new StringJoiner("\n");
            }
            joiner.add(l);
        }
        if (medicineName != null) {
            nameList.add(medicineName);
            sliceList.add(joiner.toString());
        }
    }

    private Set<String> processDirectory() throws Exception {
        Set<String> set = new TreeSet<>();
        List<String> allLines = documentLoader.readAllLines("中成药篇/directory.txt");
        for (String line : allLines) {
            StringTokenizer tokenizer = new StringTokenizer(line);
            while (tokenizer.hasMoreTokens()) {
                String tok = tokenizer.nextToken();
                if (tok.contains(SYMBOL)) {
                    int index = tok.indexOf(SYMBOL);
                    tok = tok.substring(0, index);
                    tok = tok.trim();
                    if (tok.length() > 0) {
                        set.add(tok);
                    }
                }
            }
        }
        return set;
    }
}
