package com.eight.blogserver8.service;


import com.eight.blogserver8.controller.response.*;

import com.eight.blogserver8.repository.HeartPostRepository;
import com.eight.blogserver8.repository.SubCommentRepository;
import com.eight.blogserver8.request.PostRequestDto;

import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.model.CannedAccessControlList;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.PutObjectRequest;
import com.eight.blogserver8.controller.response.CommentResponseDto;
import com.eight.blogserver8.controller.response.PostResponseDto;
import com.eight.blogserver8.controller.response.ResponseDto;
import com.eight.blogserver8.controller.response.SubCommentResponseDto;

import com.eight.blogserver8.domain.Comment;
import com.eight.blogserver8.domain.Member;
import com.eight.blogserver8.domain.Post;
import com.eight.blogserver8.domain.SubComment;
import com.eight.blogserver8.jwt.TokenProvider;
import com.eight.blogserver8.repository.CommentRepository;
import com.eight.blogserver8.repository.PostRepository;
import com.eight.blogserver8.shared.CommonUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
@Slf4j
@Service
@RequiredArgsConstructor
public class PostService {

    private final PostRepository postRepository;
    private final CommentRepository commentRepository;

    private final SubCommentRepository subCommentRepository;

    private final HeartPostRepository heartPostRepository;

    private final TokenProvider tokenProvider;

    private final AmazonS3Client amazonS3Client;

    @Value("${cloud.aws.s3.bucket}")
    private String bucketName;

    @Transactional
    public ResponseDto<?> createPost(PostRequestDto requestDto, MultipartFile multipartFile, HttpServletRequest request) throws IOException {
        if (null == request.getHeader("Refresh-Token")) {
            return ResponseDto.fail("MEMBER_NOT_FOUND",
                    "???????????? ???????????????.");
        }

        if (null == request.getHeader("Authorization")) {
            return ResponseDto.fail("MEMBER_NOT_FOUND",
                    "???????????? ???????????????.");
        }

        Member member = validateMember(request);
        if (null == member) {
            return ResponseDto.fail("INVALID_TOKEN", "Token??? ???????????? ????????????.");
        }

        String imageUrl = null;

        if (!multipartFile.isEmpty()) {
            String fileName = CommonUtils.buildFileName(multipartFile.getOriginalFilename());

            ObjectMetadata objectMetadata = new ObjectMetadata();
            objectMetadata.setContentType(multipartFile.getContentType());

            InputStream inputStream = multipartFile.getInputStream();
            amazonS3Client.putObject(new PutObjectRequest(bucketName, fileName, inputStream, objectMetadata)
                    .withCannedAcl(CannedAccessControlList.PublicRead));


            imageUrl = amazonS3Client.getUrl(bucketName, fileName).toString();
        }

        Post post = Post.builder()
                .title(requestDto.getTitle())
                .content(requestDto.getContent())
                .imageUrl(imageUrl)
                .member(member)
                .build();
        postRepository.save(post);
        return ResponseDto.success(
                PostResponseDto.builder()
                        .id(post.getId())
                        .title(post.getTitle())
                        .content(post.getContent())
                        .author(post.getMember().getNickname())
                        .createdAt(post.getCreatedAt())
                        .modifiedAt(post.getModifiedAt())
                        .imageUrl(post.getImageUrl())
                        .build()
        );

    }

    @Transactional(readOnly = true)
    public ResponseDto<?> getPost(Long id) {
        Post post = isPresentPost(id);
        if (null == post) {
            return ResponseDto.fail("200", "???????????? ?????? ????????? id ?????????.");
        }

        List<Comment> commentList = commentRepository.findAllByPost(post);
        List<CommentResponseDto> commentResponseDtoList = new ArrayList<>();

        for (Comment comment : commentList) {
            List<SubComment> cheackSubCommnet = comment.getSubComments();
            List<SubCommentResponseDto> subCommentResponseDtoList = new ArrayList<>();
            for (SubComment subComment : cheackSubCommnet) {
                subCommentResponseDtoList.add(
                        SubCommentResponseDto.builder()
                                .id(subComment.getId())
                                .nickname(subComment.getMember().getNickname())
                                .content(subComment.getContent())
                                .heart(subComment.getHeart())
                                .createdAt(subComment.getCreatedAt())
                                .modifiedAt(subComment.getModifiedAt())
                                .build()
                );
            }
            commentResponseDtoList.add(
                    CommentResponseDto.builder()
                            .id(comment.getId())
                            .author(comment.getMember().getNickname())
                            .content(comment.getContent())
                            .heart(comment.getHeart())
                            .subCommentResponseDtoList(subCommentResponseDtoList)
                            .createdAt(comment.getCreatedAt())
                            .modifiedAt(comment.getModifiedAt())
                            .build()
            );
        }

        return ResponseDto.success(
                PostResponseDto.builder()
                        .id(post.getId())
                        .title(post.getTitle())
                        .content(post.getContent())
                        .heart(post.getHeartPosts().size())
                        .commentResponseDtoList(commentResponseDtoList)
                        .author(post.getMember().getNickname())
                        .createdAt(post.getCreatedAt())
                        .modifiedAt(post.getModifiedAt())
                        .imageUrl(post.getImageUrl())
                        .build()
        );
    }



    @Transactional(readOnly = true)
    public ResponseDto<?> getAllPost() {

        //????????? ?????? response??? id, ??????, ?????????, ????????? ??????, ????????? ????????? ?????? ??????, ?????????, ????????? ????????????

        List<Post> allByOrderByModifiedAtDesc = postRepository.findAllByOrderByModifiedAtDesc();
        // ??????????????? ????????? ??????
        List<PostListResponseDto> dtoList = new ArrayList<>();

        for (Post post : allByOrderByModifiedAtDesc) {
            // ????????? ?????????
            Long postId = post.getId();
            // ????????? ????????? ?????? ??????
            long heartCount = heartPostRepository.countAllByPostId(postId);

            PostListResponseDto postListResponseDto = new PostListResponseDto(post, heartCount);
            // ?????? ?????? ???????????? ??????
            dtoList.add(postListResponseDto);
        }




        return ResponseDto.success(dtoList);


    }

    @Transactional
    public ResponseDto<Post> updatePost(Long id, PostRequestDto requestDto, HttpServletRequest request) {
        if (null == request.getHeader("Refresh-Token")) {
            return ResponseDto.fail("MEMBER_NOT_FOUND",
                    "???????????? ???????????????.");
        }

        if (null == request.getHeader("Authorization")) {
            return ResponseDto.fail("MEMBER_NOT_FOUND",
                    "???????????? ???????????????.");
        }

        Member member = validateMember(request);
        if (null == member) {
            return ResponseDto.fail("INVALID_TOKEN", "Token??? ???????????? ????????????.");
        }

        Post post = isPresentPost(id);
        if (null == post) {
            return ResponseDto.fail("200", "???????????? ?????? ????????? id ?????????.");
        }

        if (post.validateMember(member)) {
            return ResponseDto.fail("BAD_REQUEST", "???????????? ????????? ??? ????????????.");
        }

        post.update(requestDto);
        return ResponseDto.success(post);
    }

    @Transactional //?????? ??????
    public ResponseDto<Post> updateImage(Long id,MultipartFile multipartFile, HttpServletRequest request) throws IOException {
        if (null == request.getHeader("Refresh-Token")) {
            return ResponseDto.fail("MEMBER_NOT_FOUND",
                    "???????????? ???????????????.");
        }

        if (null == request.getHeader("Authorization")) {
            return ResponseDto.fail("MEMBER_NOT_FOUND",
                    "???????????? ???????????????.");
        }

        Member member = validateMember(request);
        if (null == member) {
            return ResponseDto.fail("INVALID_TOKEN", "Token??? ???????????? ????????????.");
        }

        Post post = isPresentPost(id);
        if (null == post) {
            return ResponseDto.fail("200", "???????????? ?????? ????????? id ?????????.");
        }

        if (post.validateMember(member)) {
            return ResponseDto.fail("BAD_REQUEST", "???????????? ????????? ??? ????????????.");
        }

        String imageUrl = null;
        if (!multipartFile.isEmpty()) {

            String fileName = CommonUtils.buildFileName(multipartFile.getOriginalFilename());

            ObjectMetadata objectMetadata = new ObjectMetadata();
            objectMetadata.setContentType(multipartFile.getContentType());

           try( InputStream inputStream = multipartFile.getInputStream() ) {
               amazonS3Client.putObject(new PutObjectRequest(bucketName, fileName, inputStream, objectMetadata)
                       .withCannedAcl(CannedAccessControlList.PublicRead));


               imageUrl = amazonS3Client.getUrl(bucketName, fileName).toString();

           }
           catch (IOException e){
               throw new IllegalArgumentException(String.format("?????? ?????? ??? ????????? ????????????????????? (%s)", multipartFile.getOriginalFilename()));
           }

        }

        post.updateImage(imageUrl);


        return ResponseDto.success(post);
    }


    @Transactional
    public ResponseDto<?> deletePost(Long id, HttpServletRequest request) {
        if (null == request.getHeader("Refresh-Token")) {
            return ResponseDto.fail("MEMBER_NOT_FOUND",
                    "???????????? ???????????????.");
        }

        if (null == request.getHeader("Authorization")) {
            return ResponseDto.fail("MEMBER_NOT_FOUND",
                    "???????????? ???????????????.");
        }

        Member member = validateMember(request);
        if (null == member) {
            return ResponseDto.fail("INVALID_TOKEN", "Token??? ???????????? ????????????.");
        }

        Post post = isPresentPost(id);
        if (null == post) {
            return ResponseDto.fail("200", "???????????? ?????? ????????? id ?????????.");
        }

        if (post.validateMember(member)) {
            return ResponseDto.fail("BAD_REQUEST", "???????????? ????????? ??? ????????????.");
        }

        postRepository.delete(post);
        return ResponseDto.success("delete success");
    }



    @Transactional(readOnly = true)
    public Post isPresentPost(Long id) {
        Optional<Post> optionalPost = postRepository.findById(id);
        return optionalPost.orElse(null);
    }

    @Transactional
    public Member validateMember(HttpServletRequest request) {
        if (!tokenProvider.validateToken(request.getHeader("Refresh-Token"))) {
            return null;
        }
        return tokenProvider.getMemberFromAuthentication();
    }

}
