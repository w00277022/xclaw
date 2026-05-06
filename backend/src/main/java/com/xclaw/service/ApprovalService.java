package com.xclaw.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xclaw.entity.Approval;
import com.xclaw.mapper.ApprovalMapper;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ApprovalService extends ServiceImpl<ApprovalMapper, Approval> {

    public List<Approval> listPending() {
        return list(new LambdaQueryWrapper<Approval>()
                .eq(Approval::getStatus, "PENDING")
                .orderByDesc(Approval::getCreatedAt));
    }

    public List<Approval> listAll() {
        return list(new LambdaQueryWrapper<Approval>()
                .orderByDesc(Approval::getCreatedAt));
    }
}
