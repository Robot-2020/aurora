package com.aurora.service.impl;

import com.alibaba.fastjson.JSON;
import com.aurora.model.dto.*;
import com.aurora.entity.Article;
import com.aurora.entity.Comment;
import com.aurora.entity.Talk;
import com.aurora.entity.UserInfo;
import com.aurora.enums.CommentTypeEnum;
import com.aurora.exception.BizException;
import com.aurora.mapper.ArticleMapper;
import com.aurora.mapper.CommentMapper;
import com.aurora.mapper.TalkMapper;
import com.aurora.mapper.UserInfoMapper;
import com.aurora.service.AuroraInfoService;
import com.aurora.service.CommentService;
import com.aurora.util.HTMLUtil;
import com.aurora.util.PageUtil;
import com.aurora.util.UserUtil;
import com.aurora.model.vo.CommentVO;
import com.aurora.model.vo.ConditionVO;
import com.aurora.model.dto.PageResultDTO;
import com.aurora.model.vo.ReviewVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.CollectionUtils;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.SneakyThrows;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import static com.aurora.constant.CommonConstant.*;
import static com.aurora.constant.RabbitMQConstant.EMAIL_EXCHANGE;
import static com.aurora.enums.CommentTypeEnum.*;

@Service
public class CommentServiceImpl extends ServiceImpl<CommentMapper, Comment> implements CommentService {

    @Value("${website.url}")
    private String websiteUrl;

    @Autowired
    private CommentMapper commentMapper;

    @Autowired
    private ArticleMapper articleMapper;

    @Autowired
    private TalkMapper talkMapper;

    @Autowired
    private UserInfoMapper userInfoMapper;

    @Autowired
    private AuroraInfoService auroraInfoService;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    private static final List<Integer> types = new ArrayList<>();

    @PostConstruct   // 该方法会在类的依赖注入完成后、对象初始化完成之前自动执行。它主要用于一些初始化的操作，比如资源的获取、对象的状态设置等。
    public void init() {	// 方法必须是 public 的、无参数的、返回类型为 void 的方法。
        CommentTypeEnum[] values = CommentTypeEnum.values();
        for (CommentTypeEnum value : values) {
            types.add(value.getType());	// 将 CommentTypeEnum 中所有的评论类型的 type 值添加到 types 列表中，目的是用来校验评论类型是否合法。
        }
    }

    @Override
    public void saveComment(CommentVO commentVO) {
        checkCommentVO(commentVO);
        WebsiteConfigDTO websiteConfig = auroraInfoService.getWebsiteConfig();
        Integer isCommentReview = websiteConfig.getIsCommentReview();	// 用来判断网站配置中评论是否需要审核的配置。
        commentVO.setCommentContent(HTMLUtil.filter(commentVO.getCommentContent()));	// 对评论内容进行 HTML 过滤，防止恶意注入。
        Comment comment = Comment.builder()
                .userId(UserUtil.getUserDetailsDTO().getUserInfoId())
                .replyUserId(commentVO.getReplyUserId())
                .topicId(commentVO.getTopicId())
                .commentContent(commentVO.getCommentContent())
                .parentId(commentVO.getParentId())
                .type(commentVO.getType())
                .isReview(isCommentReview == TRUE ? FALSE : TRUE)
                .build();
        commentMapper.insert(comment);	// 将评论插入数据库
        String fromNickname = UserUtil.getUserDetailsDTO().getNickname();
        if (websiteConfig.getIsEmailNotice().equals(TRUE)) {
            CompletableFuture.runAsync(() -> notice(comment, fromNickname));	// 异步发送邮件通知给回复的用户
        }
    }

    @Override
    public PageResultDTO<CommentDTO> listComments(CommentVO commentVO) {
        Integer commentCount = commentMapper.selectCount(new LambdaQueryWrapper<Comment>()
                .eq(Objects.nonNull(commentVO.getTopicId()), Comment::getTopicId, commentVO.getTopicId())
                .eq(Comment::getType, commentVO.getType())
                .isNull(Comment::getParentId)	// parentId == null 即初始评论
                .eq(Comment::getIsReview, TRUE));
        if (commentCount == 0) {
            return new PageResultDTO<>();
        }
        // 分页查询查询评论列表（SQL指定了parentId is null）
        List<CommentDTO> commentDTOs = commentMapper.listComments(PageUtil.getLimitCurrent(), PageUtil.getSize(), commentVO);
        if (CollectionUtils.isEmpty(commentDTOs)) {
            return new PageResultDTO<>();
        }
        List<Integer> commentIds = commentDTOs.stream()
                .map(CommentDTO::getId)
                .collect(Collectors.toList());
        // 根据起始评论的id 找到每个 id下面的回复（SQL指定了回复评论的parent_id 在 commentIds中）
        List<ReplyDTO> replyDTOS = commentMapper.listReplies(commentIds);
        // 将评论 与 回复的评论分组、合并
        Map<Integer, List<ReplyDTO>> replyMap = replyDTOS.stream()
                .collect(Collectors.groupingBy(ReplyDTO::getParentId));
        commentDTOs.forEach(item -> item.setReplyDTOs(replyMap.get(item.getId())));
        return new PageResultDTO<>(commentDTOs, commentCount);
    }

    @Override
    public List<ReplyDTO> listRepliesByCommentId(Integer commentId) {
        return commentMapper.listReplies(Collections.singletonList(commentId));
    }

    @Override
    public List<CommentDTO> listTopSixComments() {
        return commentMapper.listTopSixComments();
    }

    @SneakyThrows
    @Override
    public PageResultDTO<CommentAdminDTO> listCommentsAdmin(ConditionVO conditionVO) {	// 后台管理系统中查询评论列表（数量 + 分页）
        CompletableFuture<Integer> asyncCount = CompletableFuture.supplyAsync(() -> commentMapper.countComments(conditionVO));
        List<CommentAdminDTO> commentBackDTOList = commentMapper.listCommentsAdmin(PageUtil.getLimitCurrent(), PageUtil.getSize(), conditionVO);
        return new PageResultDTO<>(commentBackDTOList, asyncCount.get());
    }

    @Override
    public void updateCommentsReview(ReviewVO reviewVO) {
        List<Comment> comments = reviewVO.getIds().stream().map(item -> Comment.builder()
                        .id(item)
                        .isReview(reviewVO.getIsReview())
                        .build())
                .collect(Collectors.toList());
        this.updateBatchById(comments);	// 批量更新评论审核状态
    }

    public void checkCommentVO(CommentVO commentVO) {
        if (!types.contains(commentVO.getType())) {
            throw new BizException("参数校验异常");
        }
        if (Objects.requireNonNull(getCommentEnum(commentVO.getType())) == ARTICLE || Objects.requireNonNull(getCommentEnum(commentVO.getType())) == TALK) {
            if (Objects.isNull(commentVO.getTopicId())) {
                throw new BizException("参数校验异常");
            } else {
                if (Objects.requireNonNull(getCommentEnum(commentVO.getType())) == ARTICLE) {
                    Article article = articleMapper.selectOne(new LambdaQueryWrapper<Article>().select(Article::getId, Article::getUserId).eq(Article::getId, commentVO.getTopicId()));
                    if (Objects.isNull(article)) {
                        throw new BizException("参数校验异常");
                    }
                }
                if (Objects.requireNonNull(getCommentEnum(commentVO.getType())) == TALK) {
                    Talk talk = talkMapper.selectOne(new LambdaQueryWrapper<Talk>().select(Talk::getId, Talk::getUserId).eq(Talk::getId, commentVO.getTopicId()));
                    if (Objects.isNull(talk)) {
                        throw new BizException("参数校验异常");
                    }
                }
            }
        }
        if (Objects.requireNonNull(getCommentEnum(commentVO.getType())) == LINK
                || Objects.requireNonNull(getCommentEnum(commentVO.getType())) == ABOUT
                || Objects.requireNonNull(getCommentEnum(commentVO.getType())) == MESSAGE) {
            if (Objects.nonNull(commentVO.getTopicId())) {
                throw new BizException("参数校验异常");
            }
        }
        if (Objects.isNull(commentVO.getParentId())) {
            if (Objects.nonNull(commentVO.getReplyUserId())) {
                throw new BizException("参数校验异常");
            }
        }
        if (Objects.nonNull(commentVO.getParentId())) {
            Comment parentComment = commentMapper.selectOne(new LambdaQueryWrapper<Comment>().select(Comment::getId, Comment::getParentId, Comment::getType).eq(Comment::getId, commentVO.getParentId()));
            if (Objects.isNull(parentComment)) {
                throw new BizException("参数校验异常");
            }
            if (Objects.nonNull(parentComment.getParentId())) {
                throw new BizException("参数校验异常");
            }
            if (!commentVO.getType().equals(parentComment.getType())) {
                throw new BizException("参数校验异常");
            }
            if (Objects.isNull(commentVO.getReplyUserId())) {
                throw new BizException("参数校验异常");
            } else {
                UserInfo existUser = userInfoMapper.selectOne(new LambdaQueryWrapper<UserInfo>().select(UserInfo::getId).eq(UserInfo::getId, commentVO.getReplyUserId()));
                if (Objects.isNull(existUser)) {
                    throw new BizException("参数校验异常");
                }
            }
        }
    }

    private void notice(Comment comment, String fromNickname) {
        // 自我回复的过滤
        if (comment.getUserId().equals(comment.getReplyUserId())) {
            if (Objects.nonNull(comment.getParentId())) {
                Comment parentComment = commentMapper.selectById(comment.getParentId());
                if (parentComment.getUserId().equals(comment.getUserId())) {
                    return;
                }
            }
        }
        // 博主自我评论的过滤
        if (comment.getUserId().equals(BLOGGER_ID) && Objects.isNull(comment.getParentId())) {
            return;
        }
        // 检查是否需要发送通知给被回复的用户（即父评论的作者）
        if (Objects.nonNull(comment.getParentId())) {
            Comment parentComment = commentMapper.selectById(comment.getParentId());
            if (!comment.getReplyUserId().equals(parentComment.getUserId())
                    && !comment.getReplyUserId().equals(comment.getUserId())) {
                UserInfo userInfo = userInfoMapper.selectById(comment.getUserId());
                UserInfo replyUserinfo = userInfoMapper.selectById(comment.getReplyUserId());
                Map<String, Object> map = new HashMap<>();
                String topicId = Objects.nonNull(comment.getTopicId()) ? comment.getTopicId().toString() : "";
                String url = websiteUrl + getCommentPath(comment.getType()) + topicId;
                map.put("content", userInfo.getNickname() + "在" + Objects.requireNonNull(getCommentEnum(comment.getType())).getDesc()
                        + "的评论区@了你，"
                        + "<a style=\"text-decoration:none;color:#12addb\" href=\"" + url + "\">点击查看</a>");
                EmailDTO emailDTO = EmailDTO.builder()
                        .email(replyUserinfo.getEmail())
                        .subject(MENTION_REMIND)
                        .template("common.html")
                        .commentMap(map)
                        .build();
                rabbitTemplate.convertAndSend(EMAIL_EXCHANGE, "*", new Message(JSON.toJSONBytes(emailDTO), new MessageProperties()));
            }
            // 父评论的作者是当前评论者的情况下，跳过通知
            if (comment.getUserId().equals(parentComment.getUserId())) {
                return;
            }
        }
        // 默认情况下，通知将发送给博主（BLOGGER_ID）。
        // 如果评论是对某个用户的回复（comment.getReplyUserId() 存在），则通知该被回复的用户。
        // 如果评论是对文章或话题的评论，则通过 articleMapper 或 talkMapper 获取文章或话题的作者 ID。
        String title;
        Integer userId = BLOGGER_ID;
        String topicId = Objects.nonNull(comment.getTopicId()) ? comment.getTopicId().toString() : "";
        if (Objects.nonNull(comment.getReplyUserId())) {
            userId = comment.getReplyUserId();
        } else {
            switch (Objects.requireNonNull(getCommentEnum(comment.getType()))) {
                case ARTICLE:
                    userId = articleMapper.selectById(comment.getTopicId()).getUserId();
                    break;
                case TALK:
                    userId = talkMapper.selectById(comment.getTopicId()).getUserId();
                default:
                    break;
            }
        }
        // 获取邮件标题并发送邮件。
        if (Objects.requireNonNull(getCommentEnum(comment.getType())).equals(ARTICLE)) {
            title = articleMapper.selectById(comment.getTopicId()).getArticleTitle();
        } else {
            title = Objects.requireNonNull(getCommentEnum(comment.getType())).getDesc();
        }
        UserInfo userInfo = userInfoMapper.selectById(userId);
        if (StringUtils.isNotBlank(userInfo.getEmail())) {
            EmailDTO emailDTO = getEmailDTO(comment, userInfo, fromNickname, topicId, title);
            rabbitTemplate.convertAndSend(EMAIL_EXCHANGE, "*", new Message(JSON.toJSONBytes(emailDTO), new MessageProperties()));
        }
    }

    private EmailDTO getEmailDTO(Comment comment, UserInfo userInfo, String fromNickname, String topicId, String title) {
        EmailDTO emailDTO = new EmailDTO();
        Map<String, Object> map = new HashMap<>();
        if (comment.getIsReview().equals(TRUE)) {
            String url = websiteUrl + getCommentPath(comment.getType()) + topicId;
            if (Objects.isNull(comment.getParentId())) {
                emailDTO.setEmail(userInfo.getEmail());
                emailDTO.setSubject(COMMENT_REMIND);
                emailDTO.setTemplate("owner.html");
                String createTime = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").format(comment.getCreateTime());
                map.put("time", createTime);
                map.put("url", url);
                map.put("title", title);
                map.put("nickname", fromNickname);
                map.put("content", comment.getCommentContent());
            } else {
                Comment parentComment = commentMapper.selectOne(new LambdaQueryWrapper<Comment>().select(Comment::getUserId, Comment::getCommentContent, Comment::getCreateTime).eq(Comment::getId, comment.getParentId()));
                if (!userInfo.getId().equals(parentComment.getUserId())) {
                    userInfo = userInfoMapper.selectById(parentComment.getUserId());
                }
                emailDTO.setEmail(userInfo.getEmail());
                emailDTO.setSubject(COMMENT_REMIND);
                emailDTO.setTemplate("user.html");
                map.put("url", url);
                map.put("title", title);
                String createTime = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").format(parentComment.getCreateTime());
                map.put("time", createTime);
                map.put("toUser", userInfo.getNickname());
                map.put("fromUser", fromNickname);
                map.put("parentComment", parentComment.getCommentContent());
                if (!comment.getReplyUserId().equals(parentComment.getUserId())) {
                    UserInfo mentionUserInfo = userInfoMapper.selectById(comment.getReplyUserId());
                    if (Objects.nonNull(mentionUserInfo.getWebsite())) {
                        map.put("replyComment", "<a style=\"text-decoration:none;color:#12addb\" href=\""
                                + mentionUserInfo.getWebsite()
                                + "\">@" + mentionUserInfo.getNickname() + " " + "</a>" + parentComment.getCommentContent());
                    } else {
                        map.put("replyComment", "@" + mentionUserInfo.getNickname() + " " + parentComment.getCommentContent());
                    }
                } else {
                    map.put("replyComment", comment.getCommentContent());
                }
            }
        } else {
            String adminEmail = userInfoMapper.selectById(BLOGGER_ID).getEmail();
            emailDTO.setEmail(adminEmail);
            emailDTO.setSubject(CHECK_REMIND);
            emailDTO.setTemplate("common.html");
            map.put("content", "您收到了一条新的回复，请前往后台管理页面审核");
        }
        emailDTO.setCommentMap(map);
        return emailDTO;
    }
}
