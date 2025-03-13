//package com.aurora.config;
//
//
//import org.springframework.context.annotation.Bean;
//import org.springframework.context.annotation.Configuration;
//import springfox.documentation.builders.ApiInfoBuilder;
//import springfox.documentation.builders.PathSelectors;
//import springfox.documentation.builders.RequestHandlerSelectors;
//import springfox.documentation.service.ApiInfo;
//import springfox.documentation.service.Contact;
//import springfox.documentation.spi.DocumentationType;
//import springfox.documentation.spring.web.plugins.Docket;
//import springfox.documentation.swagger2.annotations.EnableSwagger2WebMvc;
//
//import java.util.Collections;
//
//@Configuration
//@EnableSwagger2WebMvc
//public class Knife4jConfig {
//
//    @Bean
//    public Docket createRestApi() {
//        return new Docket(DocumentationType.SWAGGER_2)
//                .protocols(Collections.singleton("https"))
//                .host("https://www.diveintodream.cn")
//                .apiInfo(apiInfo())
//                .select()
//                .apis(RequestHandlerSelectors.basePackage("com.aurora.controller"))
//                .paths(PathSelectors.any())
//                .build();
//    }
//
//    private ApiInfo apiInfo() {
//        return new ApiInfoBuilder()
//                .title("diveintodream文档")
//                .description("diveintodream")
//                .contact(new Contact("Cloud", "https://www.diveintodream.cn", "wuzhiao1119@163.com"))
//                .termsOfServiceUrl("https://www.diveintodream.cn/api")
//                .version("1.0")
//                .build();
//    }
//
//}
