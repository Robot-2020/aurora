package com.aurora.handler;

import com.aurora.model.vo.ResultVO;
import com.aurora.enums.StatusCodeEnum;
import com.aurora.exception.BizException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import java.util.Objects;

@RestControllerAdvice
//@Slf4j
public class ControllerAdviceHandler {

    @ExceptionHandler(value = BizException.class)
    public ResultVO<?> errorHandler(BizException e) {
//        log.error("BizException, " + e.getMessage());
        return ResultVO.fail(e.getCode(), e.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResultVO<?> errorHandler(MethodArgumentNotValidException e) {
        return ResultVO.fail(StatusCodeEnum.VALID_ERROR.getCode(), Objects.requireNonNull(e.getBindingResult().getFieldError()).getDefaultMessage());
    }

    @ExceptionHandler(value = Exception.class)
    public ResultVO<?> errorHandler(Exception e) {
        e.printStackTrace();
        return ResultVO.fail(StatusCodeEnum.SYSTEM_ERROR.getCode(), StatusCodeEnum.SYSTEM_ERROR.getDesc());
    }

}
