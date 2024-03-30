package com.atguigu.gulimall.search.controller;


import com.atguigu.gulimall.search.service.MallSearchService;
import com.atguigu.gulimall.search.vo.SearchParam;
import com.atguigu.gulimall.search.vo.SearchResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import javax.servlet.http.HttpServletRequest;

@Controller
public class IndexController {

    @Autowired
    private MallSearchService mallsearchService;

    @GetMapping("/list.html")
    public String index(SearchParam param, Model model, HttpServletRequest request){

        param.set_queryString( request.getQueryString() );

        // 根据传递来的查询参数，去es中检索商品
        SearchResult result = mallsearchService.search( param );

        model.addAttribute( "result", result );

        return "list";
    }

}
