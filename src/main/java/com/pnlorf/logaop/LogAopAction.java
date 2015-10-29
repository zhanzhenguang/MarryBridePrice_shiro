package com.pnlorf.logaop;

import com.pnlorf.annotation.SystemLog;
import com.pnlorf.entity.LogFormMap;
import com.pnlorf.mapper.LogMapper;
import com.pnlorf.util.Common;
import org.apache.shiro.SecurityUtils;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.AfterThrowing;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;


/**
 * 切点类
 * <p>
 * Created by 冰诺莫语 on 2015/10/27.
 */
@Aspect
@Component
public class LogAopAction {
    //本地异常日志记录对象
    private static final Logger logger = LoggerFactory.getLogger(LogAopAction.class);

    @Autowired
    private LogMapper logMapper;

    // Controller层切点
    @Pointcut("@annotation(com.pnlorf.annotation.SystemLog)")
    public void controllerAspect() {

    }

    @AfterThrowing(pointcut = "controllerAspect()", throwing = "e")
    public void doAfterThrowing(JoinPoint point, Throwable e) {
        LogFormMap logFormMap = new LogFormMap();
        Map<String, Object> map = null;
        String user = null;
        String ip = null;
        try {
            SecurityUtils.getSubject().getSession().getHost();
        } catch (Exception ee) {
            ip = "无法获取登录用户IP";
        }

        try {
            map = getControllerMethodDescription(point);
            // 登录名
            user = SecurityUtils.getSubject().getPrincipal().toString();
            if (Common.isEmpty(user)) {
                user = "无法获取用户登录信息!";
            }
        } catch (Exception ee) {
            user = "无法获取用户登录信息!";
        }

        logFormMap.put("accountName", user);
        logFormMap.put("module", map.get("module"));
        logFormMap.put("methods", "<font color=\"red\">执行方法异常:-->" + map.get("methods") + "</font>");
        logFormMap.put("description", "<font color=\"red\">执行方法异常:-->" + e + "</font>");
        logFormMap.put("actionTime", "0");
        logFormMap.put("userId", ip);
        try {
            logMapper.addEntity(logFormMap);
        } catch (Exception ee) {
            logger.error("切点异常", ee);
        }
    }

    /**
     * 前置通知 用于拦截Controller层记录用户的操作
     *
     * @param point 切点
     * @return
     */
    @Around("controllerAspect()")
    public Object doController(ProceedingJoinPoint point) {
        Object result = null;
        // 执行方法名
        String methodName = point.getSignature().getName();
        String className = point.getTarget().getClass().getSimpleName();
        LogFormMap logFormMap = new LogFormMap();
        Map<String, Object> map = null;
        String user = null;
        Long start = 0L;
        Long end = 0L;
        Long time = 0L;
        String ip = null;
        try {
            ip = SecurityUtils.getSubject().getSession().getHost();
        } catch (Exception e) {
            ip = "无法获取登录用户Ip";
        }
        try {
            // 登录名
            user = SecurityUtils.getSubject().getPrincipal().toString();
            if (Common.isEmpty(user)) {
                user = "无法获取登录用户信息！";
            }
        } catch (Exception e) {
            user = "无法获取登录用户信息！";
        }
        // 当前用户
        try {
            map = getControllerMethodDescription(point);
            // 执行方法所消耗的时间
            start = System.currentTimeMillis();
            result = point.proceed();
            end = System.currentTimeMillis();
            time = end - start;
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
        try {
            logFormMap.put("accountName", user);
            logFormMap.put("module", map.get("module"));
            logFormMap.put("methods", map.get("methods"));
            logFormMap.put("description", map.get("description"));
            logFormMap.put("actionTime", time.toString());
            logFormMap.put("userIP", ip);
            logMapper.addEntity(logFormMap);
            //*========控制台输出=========*//
            System.out.println("=====通知开始=====");
            System.out.println("请求方法:" + className + "." + methodName + "()");
            System.out.println("方法描述:" + map);
            System.out.println("请求IP:" + ip);
            System.out.println("=====通知结束=====");
        } catch (Exception e) {
            //记录本地异常日志
            logger.error("====通知异常====");
            logger.error("异常信息:{}", e.getMessage());
        }
        return result;
    }

    /**
     * 获取注解中对方法的描述信息，用户Controller层注解
     *
     * @param joinPoint 切点
     * @return 方法描述
     * @throws Exception
     */
    public Map<String, Object> getControllerMethodDescription(JoinPoint joinPoint) throws Exception {
        Map<String, Object> map = new HashMap<String, Object>();
        String targetName = joinPoint.getTarget().getClass().getName();
        String methodName = joinPoint.getSignature().getName();
        Object[] arguments = joinPoint.getArgs();
        Class targetClass = Class.forName(targetName);
        Method[] methods = targetClass.getMethods();
        for (Method method : methods) {
            if (method.getName().equals(methodName)) {
                Class[] classes = method.getParameterTypes();
                if (classes.length == arguments.length) {
                    map.put("module", method.getAnnotation(SystemLog.class).module());
                    map.put("methods", method.getAnnotation(SystemLog.class).methods());
                    String desc = method.getAnnotation(SystemLog.class).description();
                    if (Common.isEmpty(desc)) {
                        desc = "执行成功!";
                    }
                    map.put("description", desc);
                    break;
                }
            }
        }
        return map;
    }

}