package com.daxiaamu.forcelanguage;

interface ILocaleService {
    void destroy() = 16777114;
    String execute(String command) = 1;
    String setLocale(String tag, String packageName) = 2;
}
