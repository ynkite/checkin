package idusw.sbb.triplinker.domain.plan.service;

import idusw.sbb.triplinker.domain.expense.repository.ExpenseRepository;
import idusw.sbb.triplinker.domain.plan.dto.PlanCreateDto;
import idusw.sbb.triplinker.domain.plan.dto.PlanDetailResponseDto;
import idusw.sbb.triplinker.domain.plan.dto.PlanInputFormSaveDto;
import idusw.sbb.triplinker.domain.plan.entity.PlanInputForm;
import idusw.sbb.triplinker.domain.plan.entity.TravelPlan;
import idusw.sbb.triplinker.domain.plan.repository.PlanInputFormRepository;
import idusw.sbb.triplinker.domain.plan.repository.TravelPlanRepository;
import idusw.sbb.triplinker.domain.planshare.repository.TripMemberRepository;
import idusw.sbb.triplinker.domain.user.entity.User;
import idusw.sbb.triplinker.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import idusw.sbb.triplinker.domain.planshare.entity.PlanRole;

import java.util.HashMap;
import java.util.Map;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TravelPlanServiceImpl implements TravelPlanService {

    private final TravelPlanRepository travelPlanRepository;
    private final PlanInputFormRepository planInputFormRepository;
    private final UserRepository userRepository;
    private final ExpenseRepository expenseRepository;
    private final TripMemberRepository tripMemberRepository;

    @Override
    @Transactional
    public Long createPlan(Long userId, PlanCreateDto dto) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 회원입니다."));

        TravelPlan plan = TravelPlan.builder()
                .user(user)
                .destination(dto.getDestination())
                .startDate(dto.getStartDate())
                .endDate(dto.getEndDate())
                .title(dto.getTitle())
                .isPublic(dto.getIsPublic())
                .status(dto.getStatus() != null ? dto.getStatus() : "DRAFT")
                .build();

        return travelPlanRepository.save(plan).getId();
    }

    @Override
    @Transactional
    public Long saveInputForm(Long userId, Long tripId, PlanInputFormSaveDto dto) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 회원입니다."));

        TravelPlan plan = travelPlanRepository.findById(tripId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 플랜입니다."));

        boolean isOwner = plan.getUser().getId().equals(userId);
        boolean isEditor = tripMemberRepository.existsByTravelPlanIdAndUserIdAndRole(tripId, userId, PlanRole.EDITOR);
        if (!isOwner && !isEditor) {
            throw new IllegalStateException("접근 권한이 없습니다.");
        }

        PlanInputForm form = PlanInputForm.builder()
                .plan(plan)
                .user(user)
                .departure(dto.getDeparture())
                .transportType(dto.getTransportType())
                .accommodationType(dto.getAccommodationType())
                .accommodationOptions(dto.getAccommodationOptions())
                .companionType(dto.getCompanionType())
                .companionCount(dto.getCompanionCount())
                .travelStyles(dto.getTravelStyles())
                .dietaryInfo(dto.getDietaryInfo())
                .hasInfant(dto.getHasInfant())
                .hasPet(dto.getHasPet())
                .scheduleDensity(dto.getScheduleDensity())
                .budget(dto.getBudget())
                .extraNotes(dto.getExtraNotes())
                .preferenceSource(dto.getPreferenceSource() != null ? dto.getPreferenceSource() : "UI_CLICK")
                .loadedFromPlanId(dto.getLoadedFromPlanId())
                .build();

        PlanInputForm saved = planInputFormRepository.save(form);
        plan.linkInputForm(saved);

        return saved.getId();
    }

    @Override
    public PlanDetailResponseDto getPlanDetail(Long userId, Long tripId) {
        TravelPlan plan = travelPlanRepository.findById(tripId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 플랜입니다."));

//      공유 링크 열람을 위해 본인 검증 로직을 주석 처리
//        if (!plan.getUser().getId().equals(userId)) {
//            throw new IllegalStateException("접근 권한이 없습니다.");
//        }

        return new PlanDetailResponseDto(plan);
    }

    @Override
    public List<PlanDetailResponseDto> getMyPlans(Long userId) {
        return travelPlanRepository.findByUserIdOrderByCreatedAtDesc(userId)
                .stream()
                .map(PlanDetailResponseDto::new)
                .toList();
    }

    @Override
    public List<idusw.sbb.triplinker.domain.plan.dto.TripListResponseDto> getMyTripList(Long userId) {
        return travelPlanRepository.findByUserIdOrderByCreatedAtDesc(userId)
                .stream()

                // 내 여행 기록에는 '확정(FIXED)'된 일정만 노출 (DRAFT·INVITED 등은 제외)
                .filter(plan -> "FIXED".equals(plan.getStatus()))

                .map(plan -> {
                    String startStr = plan.getStartDate() != null ? plan.getStartDate().toString().replace("-", ".") : "";
                    String endStr = plan.getEndDate() != null ? plan.getEndDate().toString().replace("-", ".") : "";

                    int pax = plan.getForm() != null ? plan.getForm().getCompanionCount() : 1;
                    String metaString = startStr + " ~ " + endStr + " · " + pax + "인";

                    long budgetAmount = (plan.getForm() != null && plan.getForm().getBudget() != null)
                            ? plan.getForm().getBudget()
                            : 0L;
                    String budgetString = "₩" + String.format("%,d", budgetAmount);

                    return idusw.sbb.triplinker.domain.plan.dto.TripListResponseDto.builder()
                            .id(plan.getId())
                            .title(plan.getTitle() != null ? plan.getTitle() : plan.getDestination() + " 여행")
                            .meta(metaString)
                            .budget(budgetString)
                            .status(plan.getStatus())
                            .startDate(plan.getStartDate() != null ? plan.getStartDate().toString() : "")
                            .endDate(plan.getEndDate() != null ? plan.getEndDate().toString() : "")
                            .destination(plan.getDestination())
                            .updatedAt(plan.getUpdatedAt())
                            .build();
                })
                .toList();
    }



    @Override
    @Transactional
    public Long loadPreviousPreference(Long userId, Long tripId) {
        PlanInputForm prev = planInputFormRepository
                .findByUserIdAndPreferenceSourceNotOrderByCreatedAtDesc(userId, "AUTO_LOADED")
                .stream()
                .filter(f -> f.getPlan() != null
                        && f.getPlan().getRouteJson() != null
                        && !f.getPlan().getRouteJson().isBlank())
                .findFirst()
                .orElse(null);

        if (prev == null) return null;

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 회원입니다."));
        TravelPlan plan = travelPlanRepository.findById(tripId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 플랜입니다."));

        PlanInputForm copied = PlanInputForm.builder()
                .plan(plan)
                .user(user)
                .departure(prev.getDeparture())
                .transportType(prev.getTransportType())
                .accommodationType(prev.getAccommodationType())
                .accommodationOptions(prev.getAccommodationOptions())
                .companionType(prev.getCompanionType())
                .companionCount(prev.getCompanionCount())
                .travelStyles(prev.getTravelStyles())
                .dietaryInfo(prev.getDietaryInfo())
                .hasInfant(prev.getHasInfant())
                .hasPet(prev.getHasPet())
                .scheduleDensity(prev.getScheduleDensity())
                .budget(prev.getBudget())
                .extraNotes(prev.getExtraNotes())
                .preferenceSource("AUTO_LOADED")
                .loadedFromPlanId(prev.getPlan().getId())
                .build();

        PlanInputForm saved = planInputFormRepository.save(copied);
        plan.linkInputForm(saved);

        return saved.getId();
    }

    @Override
    @Transactional
    public void updateInputForm(Long userId, Long tripId, java.util.Map<String, String> fields) {
        String destination = fields.get("destination");
        if (destination != null) {
            travelPlanRepository.updateDestination(tripId, destination);
        }

        PlanInputForm form = planInputFormRepository.findByPlanIdWithLock(tripId).orElse(null);
        if (form == null) return;

        if (fields.containsKey("budget"))
            form.updateByChat("budget", fields.get("budget").replaceAll("[^0-9]", ""));
        if (fields.containsKey("transportType"))
            form.updateByChat("transportType", fields.get("transportType"));
        if (fields.containsKey("accommodationType"))
            form.updateByChat("accommodationType", fields.get("accommodationType"));
        if (fields.containsKey("scheduleDensity"))
            form.updateByChat("scheduleDensity", fields.get("scheduleDensity"));
        if (fields.containsKey("companionCount"))
            form.updateByChat("companionCount", fields.get("companionCount"));
        if (fields.containsKey("hasPet"))
            form.updateByChat("hasPet", fields.get("hasPet"));
        if (fields.containsKey("extraNotes"))
            form.updateByChat("extraNotes", fields.get("extraNotes"));

        TravelPlan plan = form.getPlan();
        if (plan != null) {
            plan.setUpdatedAt(java.time.LocalDateTime.now());
            travelPlanRepository.save(plan);
        }
    }
    @Override
    @Transactional(readOnly = true)
    public Map<String, Object> getLatestPreference(Long userId) {
        return planInputFormRepository
                .findByUserIdAndPreferenceSourceNotOrderByCreatedAtDesc(userId, "AUTO_LOADED")
                .stream()
                .filter(f -> f.getPlan() != null
                        && f.getPlan().getRouteJson() != null
                        && !f.getPlan().getRouteJson().isBlank())
                .findFirst()
                .map(form -> {
                    Map<String, Object> m = new HashMap<>();
                    m.put("departure",            form.getDeparture());
                    m.put("transportType",        form.getTransportType());
                    m.put("accommodationType",    form.getAccommodationType());
                    m.put("accommodationOptions", form.getAccommodationOptions());
                    m.put("companionType",        form.getCompanionType());
                    m.put("companionCount",       form.getCompanionCount());
                    m.put("travelStyles",         form.getTravelStyles());
                    m.put("dietaryInfo",          form.getDietaryInfo());
                    m.put("hasInfant",            form.getHasInfant());
                    m.put("hasPet",               form.getHasPet());
                    m.put("scheduleDensity",      form.getScheduleDensity());
                    m.put("budget",               form.getBudget());
                    m.put("extraNotes",           form.getExtraNotes());
                    m.put("destination",          form.getPlan().getDestination());
                    m.put("startDate",            form.getPlan().getStartDate() != null ? form.getPlan().getStartDate().toString() : null);
                    m.put("endDate",              form.getPlan().getEndDate()   != null ? form.getPlan().getEndDate().toString()   : null);
                    return m;
                })
                .orElse(null);
    }

    @Override
    public java.util.Map<String, Object> getInputFormMap(Long tripId) {
        // DB에 데이터를 반환
        TravelPlan plan = travelPlanRepository.findById(tripId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 플랜입니다."));

        java.util.Map<String, Object> map = new java.util.HashMap<>();

        if (plan.getForm() != null) {
            map.put("companionCount", plan.getForm().getCompanionCount());
            map.put("transportType", plan.getForm().getTransportType());
        } else {
            map.put("companionCount", "2");
            map.put("transportType", "자차");
        }

        return map;
    }



    // 초대받은 링크 보관
    @Override
    @Transactional
    public void saveInvitedPlan(Long userId, Long originalTripId, String inviteUrl, String title, String destination) {
        User user = userRepository.findById(userId).orElseThrow();

        TravelPlan plan = TravelPlan.builder()
                .user(user)
                .title(title)
                .destination(destination != null ? destination : "공유받은 지역")
                .startDate(java.time.LocalDate.now())
                .endDate(java.time.LocalDate.now())
                .status("INVITED") // 내 플랜과 섞이지 않도록 상태 분리
                .routeJson(inviteUrl) // 링크를 통째로 보관
                .scrapedFromPlanId(originalTripId) // 원래 플랜 번호
                .build();
        travelPlanRepository.save(plan);
    }
    @Override
    public List<Map<String, Object>> getInvitedPlans(Long userId) {
        return travelPlanRepository.findByUserIdOrderByCreatedAtDesc(userId)
                .stream()
                .filter(plan -> "INVITED".equals(plan.getStatus()))
                .map(plan -> {
                    Map<String, Object> m = new HashMap<>();
                    m.put("id", plan.getId());
                    m.put("originalTripId", plan.getScrapedFromPlanId());
                    m.put("url", plan.getRouteJson());
                    m.put("title", plan.getTitle());
                    m.put("destination", plan.getDestination());
                    m.put("savedAt", plan.getCreatedAt());
                    return m;
                })
                .toList();
    }
    @Override
    @Transactional
    public void deleteInvitedPlan(Long userId, Long planId) {
        travelPlanRepository.findById(planId).ifPresent(plan -> {
            if (plan.getUser().getId().equals(userId) && "INVITED".equals(plan.getStatus())) {

                // 초대받은 일정폼 (PlanInputForm) 삭제
                planInputFormRepository.findByPlanId(planId).ifPresent(form -> {
                    planInputFormRepository.delete(form);
                });

                // 초대받은 일정 가계부(Expense) 삭제
                expenseRepository.findByPlanId(planId).forEach(expense -> {
                    expenseRepository.delete(expense);
                });

                // 초대받은 일정 멤버 꼬임 방지
                tripMemberRepository.findByTravelPlanId(planId).forEach(member -> {
                    tripMemberRepository.delete(member);
                });

                // 부모 데이터  삭제
                travelPlanRepository.delete(plan);
            }
        });
    }



    @Override
    @Transactional
    public void updatePlanStatus(Long userId, Long tripId, String status) {
        TravelPlan plan = travelPlanRepository.findById(tripId)
                .orElseThrow(() -> new IllegalArgumentException("플랜을 찾을 수 없습니다."));
        boolean isOwner = plan.getUser().getId().equals(userId);
        boolean isEditor = tripMemberRepository.existsByTravelPlanIdAndUserIdAndRole(tripId, userId, PlanRole.EDITOR);
        if (!isOwner && !isEditor) {
            throw new IllegalStateException("접근 권한이 없습니다.");
        }
        // 확정(FIXED) / 수정 중(DRAFT) 두 가지만 허용
        if (!"FIXED".equals(status) && !"DRAFT".equals(status)) {
            throw new IllegalArgumentException("허용되지 않은 상태값입니다.");
        }
        if ("FIXED".equals(status)) {
            // 확정: 수정 중(draft) 내용이 있으면 확정본으로 승격하고 draft를 비운다.
            plan.confirmDraft();
        } else {
            plan.setStatus(status);
        }
        plan.setUpdatedAt(java.time.LocalDateTime.now());
        travelPlanRepository.save(plan);
    }

    @Override
    @Transactional
    public void deletePlan(Long userId, Long tripId) {
        travelPlanRepository.findById(tripId).ifPresent(plan -> {
            if (plan.getUser().getId().equals(userId)) {

                // 플랜에 엮인 취향 폼(PlanInputForm)  삭제
                planInputFormRepository.findByPlanId(tripId).ifPresent(form -> {
                    planInputFormRepository.delete(form);
                });

                // 가계부 지출 내역(Expense) 삭제
                expenseRepository.findByPlanId(tripId).forEach(expense -> {
                    expenseRepository.delete(expense);
                });

                // 이 플랜에 엮인 공유 멤버(TripMember) 삭제
                tripMemberRepository.findByTravelPlanId(tripId).forEach(member -> {
                    tripMemberRepository.delete(member);
                });

                // 다른 사람들이 초대받은 일정으로 보관해둔 복사본(INVITED) 삭제
                travelPlanRepository.findByScrapedFromPlanId(tripId).forEach(invitedPlan -> {
                    travelPlanRepository.delete(invitedPlan);
                });

                // TravelPlan 삭제
                travelPlanRepository.delete(plan);
            }
        });
    }


}
