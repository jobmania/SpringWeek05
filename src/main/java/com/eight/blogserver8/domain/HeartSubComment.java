package com.eight.blogserver8.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import javax.persistence.*;

@Builder
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Entity
public class HeartSubComment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JoinColumn(name = "member_id", nullable = false)
    @ManyToOne(fetch = FetchType.LAZY)
    private Member member;

    @JoinColumn(name = "subComment_id", nullable = false)
    @ManyToOne(fetch = FetchType.LAZY)
    private SubComment subComment;


    public boolean validateMember(Member member) {
        return !this.member.equals(member);
    }
}
