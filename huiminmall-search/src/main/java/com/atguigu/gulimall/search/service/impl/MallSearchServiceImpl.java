package com.atguigu.gulimall.search.service.impl;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.TypeReference;
import com.atguigu.common.es.SkuEsModel;
import com.atguigu.common.utils.R;
import com.atguigu.gulimall.search.config.GulimallElasticsearchConfig;
import com.atguigu.gulimall.search.constant.EsConstant;
import com.atguigu.gulimall.search.feign.ProductFeignService;
import com.atguigu.gulimall.search.service.MallSearchService;
import com.atguigu.gulimall.search.vo.AttrResponseVo;
import com.atguigu.gulimall.search.vo.SearchParam;
import com.atguigu.gulimall.search.vo.SearchResult;
import org.apache.lucene.search.join.ScoreMode;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.NestedQueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.query.RangeQueryBuilder;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHits;
import org.elasticsearch.search.aggregations.Aggregation;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.bucket.nested.NestedAggregationBuilder;
import org.elasticsearch.search.aggregations.bucket.nested.ParsedNested;
import org.elasticsearch.search.aggregations.bucket.terms.ParsedLongTerms;
import org.elasticsearch.search.aggregations.bucket.terms.ParsedStringTerms;
import org.elasticsearch.search.aggregations.bucket.terms.Terms;
import org.elasticsearch.search.aggregations.bucket.terms.TermsAggregationBuilder;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.fetch.subphase.highlight.HighlightBuilder;
import org.elasticsearch.search.fetch.subphase.highlight.HighlightField;
import org.elasticsearch.search.sort.SortOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;


@Service
public class MallSearchServiceImpl implements MallSearchService {

    @Autowired
    private RestHighLevelClient restHighLevelClient;

    @Autowired
    private ProductFeignService productFeignService;

    @Override
    public SearchResult search(SearchParam param) {

        // 1. 动态构建出查询需要的DSL语句
        SearchResult result = null;

        try {
            // 准备检索请求
            SearchRequest searchRequest = buildSearchRequest( param );

            // 2.执行检索请求
            SearchResponse response = restHighLevelClient.search(searchRequest, GulimallElasticsearchConfig.COMMON_OPTIONS);

            // 3.封装响应结果
            result = buildSearchResult( response, param);
        } catch (IOException e) {
            e.printStackTrace();
        }

        return result;
    }


    /**
     * 准备检索请求
     * 模糊匹配，过滤（按照属性，分类，品牌，价格区间，库存），排序，分页，高亮，聚合分析
     * @param param
     * @return
     */
    private SearchRequest buildSearchRequest(SearchParam param) {

        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();

        /**
         * 模糊匹配，过滤（按照属性，分类，品牌，价格区间，库存）
         */
        // 1.构建bool-query
        BoolQueryBuilder boolQueryBuilder = new BoolQueryBuilder();

        // 1.1 bool-must
        if ( !StringUtils.isEmpty( param.getKeyword())){
            boolQueryBuilder.must(QueryBuilders.matchQuery("skuTitle", param.getKeyword()));
        }

        // 1.2 bool-filter
        // 1.2.1 catelogId
        if ( null != param.getCatelog3Id() ){
            boolQueryBuilder.filter( QueryBuilders.termQuery("catalogId", param.getCatelog3Id()));
        }

        // 1.2.2 brandId
        if ( null != param.getBrandId() && param.getBrandId().size() > 0){
            boolQueryBuilder.filter( QueryBuilders.termsQuery("brandId", param.getBrandId()));
        }

        // 1.2.3 attrs
        if( param.getAttrs() != null && param.getAttrs().size() > 0 ){
            param.getAttrs().forEach( item -> {
                ////attrs=1_5寸:8寸&2_16G:8G
                BoolQueryBuilder boolQuery = QueryBuilders.boolQuery();

                //attrs=1_5寸:8寸
                String[] s = item.split("_");
                String attrId = s[0];
                String[] attrValues = s[1].split(":");
                boolQuery.must(QueryBuilders.termQuery("attrs.attrId", attrId));
                boolQuery.must(QueryBuilders.termsQuery("attrs.attrValue", attrValues));

                NestedQueryBuilder nestedQueryBuilder = QueryBuilders.nestedQuery("attrs", boolQuery, ScoreMode.None);

                boolQueryBuilder.filter( nestedQueryBuilder );
            });
        }

        // 1.2.4 hasStock
        if ( null != param.getHasStock() ){
            boolQueryBuilder.filter( QueryBuilders.termQuery("hasStock", param.getHasStock() == 1));
        }

        // 1.2.5 skuPrice
        if ( !StringUtils.isEmpty(param.getSkuPrice())){
            //skuPrice形式为：1_500或_500或500_
            RangeQueryBuilder rangeQueryBuilder = QueryBuilders.rangeQuery("skuPrice");

            String[] price = param.getSkuPrice().split("_");
            if ( price.length == 2 ){
                rangeQueryBuilder.gte( price[0] );
                rangeQueryBuilder.lte( price[1] );
            }else if ( price.length == 1 ){
                if ( param.getSkuPrice().startsWith("_")){
                    rangeQueryBuilder.lte( price[1] );
                }else if ( param.getSkuPrice().endsWith("_")){
                    rangeQueryBuilder.gte( price[0]);
                }
            }
            boolQueryBuilder.filter( rangeQueryBuilder );
        }

        // 封装所有的查询条件
        searchSourceBuilder.query( boolQueryBuilder );


        /**
         * 排序，分页，高亮
         */
        // 1. 排序
        //形式为sort=hotScore_asc/desc
        if ( !StringUtils.isEmpty( param.getSort())){
            String[] sortFields = param.getSort().split("_");

            SortOrder sortOrder = "asc".equalsIgnoreCase(sortFields[1]) ? SortOrder.ASC : SortOrder.DESC;
            searchSourceBuilder.sort( sortFields[0], sortOrder);
        }

        //分页
        searchSourceBuilder.from( (param.getPageNum()-1)* EsConstant.PRODUCT_PAGESIZE);
        searchSourceBuilder.size( EsConstant.PRODUCT_PAGESIZE);

        // 高亮
        if ( !StringUtils.isEmpty( param.getKeyword())){
            HighlightBuilder highlightBuilder = new HighlightBuilder();
            highlightBuilder.field("skuTitle");
            highlightBuilder.preTags("<b style='color:red'>");
            highlightBuilder.postTags("</b>");

            searchSourceBuilder.highlighter( highlightBuilder );
        }

        /**
         * 聚合分析
         */
        //1. 按照品牌进行聚合
        TermsAggregationBuilder brand_agg = AggregationBuilders.terms("brand_agg");
        brand_agg.field("brandId").size(50);

        // 1.1 品牌的子聚合-品牌名聚合
        brand_agg.subAggregation( AggregationBuilders.terms("brand_name_agg").field("brandName").size(1));
        // 1.2 品牌的子聚合-品牌图片聚合
        brand_agg.subAggregation( AggregationBuilders.terms("brand_img_agg").field("brandImg").size(1));

        searchSourceBuilder.aggregation( brand_agg );

        // 2. 按照分类信息进行聚合
        TermsAggregationBuilder catelog_agg = AggregationBuilders.terms("catalog_agg");
        catelog_agg.field("catalogId").size(20);

        catelog_agg.subAggregation( AggregationBuilders.terms("catalog_name_agg").field("catalogName").size(1));

        searchSourceBuilder.aggregation( catelog_agg );

        // 3.按照属性信息进行聚合
        NestedAggregationBuilder attr_agg = AggregationBuilders.nested("attr_agg", "attrs");
        // 3.1 按照属性ID进行聚合
        TermsAggregationBuilder attr_id_agg = AggregationBuilders.terms("attr_id_agg").field("attrs.attrId");


        // 3.1.1 在每个属性id下，按照属性名称进行聚合
        attr_id_agg.subAggregation( AggregationBuilders.terms("attr_name_agg").field("attrs.attrName").size(1));
        // 3.1.2 在每个属性ID下，按照属性值进行聚合
        attr_id_agg.subAggregation( AggregationBuilders.terms("attr_value_agg").field("attrs.attrValue").size(1));

        attr_agg.subAggregation( attr_id_agg );
        searchSourceBuilder.aggregation( attr_agg );

        System.out.println("构建的DSL语句" + searchSourceBuilder.toString());

        SearchRequest searchRequest = new SearchRequest(new String[]{EsConstant.PRODUCT_INDEX}, searchSourceBuilder);

        return searchRequest;
    }

    /**
     * 封装响应结果
     * 模糊匹配，过滤（按照属性、分类、品牌，价格区间，库存），完成排序、分页、高亮,聚合分析功能
     * @param response
     * @param param
     * @return
     */
    private SearchResult buildSearchResult(SearchResponse response, SearchParam param) {

        SearchResult result = new SearchResult();

        // 1.返回的所有查询到的商品
        SearchHits hits = response.getHits();

        List<SkuEsModel> esModels = new ArrayList<>();
        // 遍历素有商品
        if ( hits.getHits() != null && hits.getHits().length > 0 ){
            for (SearchHit hit : hits.getHits()) {
                String sourceAsString = hit.getSourceAsString();
                SkuEsModel skuEsModel = JSON.parseObject(sourceAsString, SkuEsModel.class);

                // 判断是否按关键字检索，如果是就显示高亮，否则不显示
                if ( !StringUtils.isEmpty( param.getKeyword())){
                    // 拿到高亮信息显示标题
                    HighlightField skuTitle = hit.getHighlightFields().get("skuTitle");
                    String skuTitleValue = skuTitle.getFragments()[0].string();
                    skuEsModel.setSkuTitle( skuTitleValue );
                }
                esModels.add( skuEsModel );
            }
            result.setProducts( esModels );
        }

        //2、当前商品涉及到的所有属性信息
        List<SearchResult.AttrVo> attrVos = new ArrayList<>();

        // 获取属性信息的聚合
        ParsedNested attrsAgg = response.getAggregations().get("attr_agg");
        ParsedLongTerms attrIdAgg = attrsAgg.getAggregations().get("attr_id_agg");

        for (Terms.Bucket bucket : attrIdAgg.getBuckets()) {
            SearchResult.AttrVo attrVo = new SearchResult.AttrVo();

            // 得到属性id
            attrVo.setAttrId( bucket.getKeyAsNumber().longValue());

            // 得到属性名称
            ParsedStringTerms attrNameAgg = bucket.getAggregations().get("attr_name_agg");
            attrVo.setAttrName( attrNameAgg.getBuckets().get(0).getKeyAsString());

            // 得到属性的所有值
            ParsedStringTerms attrValueAgg = bucket.getAggregations().get("attr_value_agg");
            List<String> attrValues = attrValueAgg.getBuckets().stream().map(item -> item.getKeyAsString()).collect(Collectors.toList());
            attrVo.setAttrValue( attrValues );

            attrVos.add( attrVo );
        }

        result.setAttrs( attrVos );

        // 3、当前商品涉及到的所有品牌信息
        List<SearchResult.BrandVo> brandVos = new ArrayList<>();
        // 获取品牌的聚合
        ParsedLongTerms brandAgg = response.getAggregations().get("brand_agg");
        for (Terms.Bucket bucket : brandAgg.getBuckets()) {
            SearchResult.BrandVo brandVo = new SearchResult.BrandVo();

            // 1.得到品牌id
            long brandId = bucket.getKeyAsNumber().longValue();
            brandVo.setBrandId( brandId );

            // 2.得到品牌名字
            ParsedStringTerms brandNameAgg = bucket.getAggregations().get("brand_name_agg");
            String brandName = brandNameAgg.getBuckets().get(0).getKeyAsString();
            brandVo.setBrandName( brandName );

            // 3.得到品牌图片
            ParsedStringTerms brandImgAgg = bucket.getAggregations().get("brand_img_agg");
            String brandImg = brandImgAgg.getBuckets().get(0).getKeyAsString();
            brandVo.setBrandImg( brandImg );

            brandVos.add( brandVo );
        }
        result.setBrands( brandVos );

        // 4、当前商品涉及到的所有分类信息
        // 获取到分类的聚合
        List<SearchResult.CatalogVo> catalogVos = new ArrayList<>();
        ParsedLongTerms catalogAgg = response.getAggregations().get("catalog_agg");
        for (Terms.Bucket bucket : catalogAgg.getBuckets()) {
            SearchResult.CatalogVo catalogVo = new SearchResult.CatalogVo();

            // 得到分类id
            long catalogId = bucket.getKeyAsNumber().longValue();
            catalogVo.setCatalogId( catalogId );

            //得到分类名
            ParsedStringTerms catalogNameAgg = bucket.getAggregations().get("catalog_name_agg");
            String catalogName = catalogNameAgg.getBuckets().get(0).getKeyAsString();
            catalogVo.setCatalogName( catalogName );

            catalogVos.add( catalogVo );
        }
        result.setCatalogs( catalogVos );
        //===============以上可以从聚合信息中获取====================//


        // 5、分页信息-页码
        result.setPageNum( param.getPageNum() );
        //5、1分页信息、总记录数
        long total = hits.getTotalHits().value;
        result.setTotal(total);

        // 5.2 分页信息，总页码
        int totalPage = (int)total % EsConstant.PRODUCT_PAGESIZE == 0?
                (int)total / EsConstant.PRODUCT_PAGESIZE:
                (int)total / EsConstant.PRODUCT_PAGESIZE + 1;

        result.setTotalPages( totalPage );

        List<Integer> pageNavs = new ArrayList<>();
        for (int i = 0; i < totalPage; i++) {
            pageNavs.add(i+1);
        }
        result.setPageNavs( pageNavs );


        //6、构建面包屑导航
        if (param.getAttrs() != null && param.getAttrs().size() > 0) {
            List<SearchResult.NavVo> collect = param.getAttrs().stream().map(attr -> {
                //1、分析每一个attrs传过来的参数值
                SearchResult.NavVo navVo = new SearchResult.NavVo();
                String[] s = attr.split("_");
                navVo.setNavValue(s[1]);
                R r = productFeignService.attrInfo(Long.parseLong(s[0]));
                if (r.getCode() == 0) {
                    AttrResponseVo data = r.getData("attr", new TypeReference<AttrResponseVo>() {
                    });
                    navVo.setNavName(data.getAttrName());
                } else {
                    navVo.setNavName(s[0]);
                }

                //2、取消了这个面包屑以后，我们要跳转到哪个地方，将请求的地址url里面的当前置空
                //拿到所有的查询条件，去掉当前
                String encode = null;
                try {
                    encode = URLEncoder.encode(attr,"UTF-8");
                    encode.replace("+","%20");  //浏览器对空格的编码和Java不一样，差异化处理
                } catch (UnsupportedEncodingException e) {
                    e.printStackTrace();
                }
                String replace = param.get_queryString().replace("&attrs=" + attr, "");
                navVo.setLink("http://search.gulimall.com/list.html?" + replace);

                return navVo;
            }).collect(Collectors.toList());

            result.setNavs(collect);
        }

        return result;
    }
}
