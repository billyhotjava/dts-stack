package com.yuzhi.dts.copilot.analytics.service;

import com.yuzhi.dts.copilot.analytics.domain.AnalyticsGroup;
import com.yuzhi.dts.copilot.analytics.domain.AnalyticsGroupMembership;
import com.yuzhi.dts.copilot.analytics.domain.AnalyticsUser;
import com.yuzhi.dts.copilot.analytics.repository.AnalyticsGroupMembershipRepository;
import com.yuzhi.dts.copilot.analytics.repository.AnalyticsGroupRepository;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class GroupService {

    public static final long ALL_USERS_GROUP_ID = 1L;
    public static final long ADMIN_GROUP_ID = 2L;

    private final AnalyticsGroupRepository groupRepository;
    private final AnalyticsGroupMembershipRepository membershipRepository;

    public GroupService(AnalyticsGroupRepository groupRepository, AnalyticsGroupMembershipRepository membershipRepository) {
        this.groupRepository = groupRepository;
        this.membershipRepository = membershipRepository;
    }

    public void ensureSystemGroupsExist() {
        ensureGroup(ALL_USERS_GROUP_ID, "All Users", "system");
        ensureGroup(ADMIN_GROUP_ID, "Administrators", "system");
    }

    public void ensureUserInDefaultGroups(AnalyticsUser user) {
        ensureMembership(ALL_USERS_GROUP_ID, user.getId(), false);
        if (user.isSuperuser()) {
            ensureMembership(ADMIN_GROUP_ID, user.getId(), true);
        }
    }

    public void ensureUserGroupAssignments(AnalyticsUser user, List<Long> requestedGroupIds) {
        ensureSystemGroupsExist();

        Set<Long> desired = new LinkedHashSet<>();
        desired.add(ALL_USERS_GROUP_ID);
        if (requestedGroupIds != null) {
            desired.addAll(requestedGroupIds);
        }
        if (user.isSuperuser()) {
            desired.add(ADMIN_GROUP_ID);
        }

        List<AnalyticsGroupMembership> existing = membershipRepository.findAllByUserId(user.getId());
        for (AnalyticsGroupMembership membership : existing) {
            if (membership.getGroupId().equals(ALL_USERS_GROUP_ID) || (user.isSuperuser() && membership.getGroupId().equals(ADMIN_GROUP_ID))) {
                continue;
            }
            if (!desired.contains(membership.getGroupId())) {
                membershipRepository.delete(membership);
            }
        }

        for (Long groupId : desired) {
            boolean manager = user.isSuperuser() && groupId.equals(ADMIN_GROUP_ID);
            ensureMembership(groupId, user.getId(), manager);
        }
    }

    public List<Long> groupIdsForUser(AnalyticsUser user) {
        Set<Long> groupIds = new LinkedHashSet<>();
        List<AnalyticsGroupMembership> memberships = membershipRepository.findAllByUserId(user.getId());
        for (AnalyticsGroupMembership membership : memberships) {
            groupIds.add(membership.getGroupId());
        }
        if (groupIds.isEmpty()) {
            groupIds.add(ALL_USERS_GROUP_ID);
            if (user.isSuperuser()) {
                groupIds.add(ADMIN_GROUP_ID);
            }
        }
        return new ArrayList<>(groupIds);
    }

    private void ensureGroup(long id, String name, String groupType) {
        Optional<AnalyticsGroup> existing = groupRepository.findById(id);
        if (existing.isPresent()) {
            return;
        }
        AnalyticsGroup group = new AnalyticsGroup();
        group.setId(id);
        group.setName(name);
        group.setGroupType(groupType);
        groupRepository.save(group);
    }

    private void ensureMembership(long groupId, long userId, boolean manager) {
        Optional<AnalyticsGroupMembership> existing = membershipRepository.findByGroupIdAndUserId(groupId, userId);
        if (existing.isPresent()) {
            AnalyticsGroupMembership membership = existing.get();
            if (membership.isGroupManager() != manager) {
                membership.setGroupManager(manager);
                membershipRepository.save(membership);
            }
            return;
        }
        AnalyticsGroupMembership membership = new AnalyticsGroupMembership();
        membership.setGroupId(groupId);
        membership.setUserId(userId);
        membership.setGroupManager(manager);
        membershipRepository.save(membership);
    }
}

