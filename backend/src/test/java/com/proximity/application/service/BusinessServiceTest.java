package com.proximity.application.service;

import com.proximity.application.dto.BusinessCreateRequest;
import com.proximity.application.dto.BusinessDetailResponse;
import com.proximity.application.dto.BusinessUpdateRequest;
import com.proximity.application.exception.BusinessNotFoundException;
import com.proximity.application.exception.BusinessOwnershipException;
import com.proximity.application.exception.DuplicateBusinessException;
import com.proximity.application.port.out.BusinessReadPort;
import com.proximity.application.port.out.BusinessWritePort;
import com.proximity.application.port.out.CachePort;
import com.proximity.domain.Business;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class BusinessServiceTest {

    @Mock
    private BusinessWritePort businessWritePort;

    @Mock
    private BusinessReadPort businessReadPort;

    @Mock
    private CachePort cachePort;

    @InjectMocks
    private BusinessService businessService;

    private static final Long OWNER_ID = 1L;
    private static final Long BUSINESS_ID = 100L;
    private static final Long OTHER_OWNER_ID = 999L;

    private Business createTestBusiness() {
        Business business = new Business("테스트 카페", "서울시 강남구", 37.5665, 126.9780,
                "02-1234-5678", "카페", OWNER_ID);
        business.setId(BUSINESS_ID);
        return business;
    }

    @Nested
    @DisplayName("getDetail")
    class GetDetail {

        @Test
        @DisplayName("Cache Hit 시 BusinessReadPort를 호출하지 않는다")
        void getDetail_cacheHit_doesNotCallReadPort() {
            // given
            BusinessDetailResponse cached = new BusinessDetailResponse(
                    BUSINESS_ID, "테스트 카페", "서울시 강남구", 37.5665, 126.9780,
                    "02-1234-5678", "카페", OWNER_ID, null, null, null, null);

            given(cachePort.getBusinessCache(BUSINESS_ID)).willReturn(Optional.of(cached));

            // when
            BusinessDetailResponse result = businessService.getDetail(BUSINESS_ID);

            // then
            assertThat(result).isEqualTo(cached);
            then(businessReadPort).should(never()).findById(anyLong());
        }

        @Test
        @DisplayName("Cache Miss 시 BusinessReadPort 호출 후 결과를 캐싱한다")
        void getDetail_cacheMiss_callsReadPortAndCaches() {
            // given
            BusinessDetailResponse fromDb = new BusinessDetailResponse(
                    BUSINESS_ID, "테스트 카페", "서울시 강남구", 37.5665, 126.9780,
                    "02-1234-5678", "카페", OWNER_ID, null, null, null, null);

            given(cachePort.getBusinessCache(BUSINESS_ID)).willReturn(Optional.empty());
            given(businessReadPort.findById(BUSINESS_ID)).willReturn(Optional.of(fromDb));

            // when
            BusinessDetailResponse result = businessService.getDetail(BUSINESS_ID);

            // then
            assertThat(result).isEqualTo(fromDb);
            then(cachePort).should().putBusinessCache(BUSINESS_ID, fromDb);
        }

        @Test
        @DisplayName("Cache Miss + DB에도 없으면 BusinessNotFoundException")
        void getDetail_notFound_throwsException() {
            // given
            given(cachePort.getBusinessCache(BUSINESS_ID)).willReturn(Optional.empty());
            given(businessReadPort.findById(BUSINESS_ID)).willReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> businessService.getDetail(BUSINESS_ID))
                    .isInstanceOf(BusinessNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("create")
    class Create {

        @Test
        @DisplayName("정상 생성 시 save 호출 및 캐시 무효화")
        void createSuccess() {
            // given
            BusinessCreateRequest request = new BusinessCreateRequest(
                    "새 카페", "서울시 강남구", 37.5665, 126.9780,
                    "02-1234-5678", "카페", null);

            given(businessWritePort.existsByOwnerAndNameAndLocation(
                    eq(OWNER_ID), eq("새 카페"), eq(37.5665), eq(126.9780)))
                    .willReturn(false);

            Business saved = new Business("새 카페", "서울시 강남구", 37.5665, 126.9780,
                    "02-1234-5678", "카페", OWNER_ID);
            saved.setId(BUSINESS_ID);
            given(businessWritePort.save(any(Business.class))).willReturn(saved);

            // when
            BusinessDetailResponse result = businessService.create(request, OWNER_ID);

            // then
            assertThat(result.name()).isEqualTo("새 카페");
            then(businessWritePort).should().save(any(Business.class));
            then(cachePort).should().invalidateSearchCache(37.5665, 126.9780);
        }

        @Test
        @DisplayName("중복 사업장 → DuplicateBusinessException (409)")
        void createDuplicate() {
            // given
            BusinessCreateRequest request = new BusinessCreateRequest(
                    "기존 카페", "서울시 강남구", 37.5665, 126.9780,
                    null, null, null);

            given(businessWritePort.existsByOwnerAndNameAndLocation(
                    eq(OWNER_ID), eq("기존 카페"), eq(37.5665), eq(126.9780)))
                    .willReturn(true);

            // when & then
            assertThatThrownBy(() -> businessService.create(request, OWNER_ID))
                    .isInstanceOf(DuplicateBusinessException.class);

            then(businessWritePort).should(never()).save(any());
        }
    }

    @Nested
    @DisplayName("update")
    class Update {

        @Test
        @DisplayName("정상 수정 시 save 호출 및 캐시 무효화")
        void updateSuccess() {
            // given
            Business existing = createTestBusiness();
            given(businessWritePort.findByIdForWrite(BUSINESS_ID)).willReturn(Optional.of(existing));

            Business updated = createTestBusiness();
            updated.setName("수정된 카페");
            given(businessWritePort.save(any(Business.class))).willReturn(updated);

            BusinessUpdateRequest request = new BusinessUpdateRequest(
                    "수정된 카페", null, null, null, null, null, null);

            // when
            BusinessDetailResponse result = businessService.update(BUSINESS_ID, request, OWNER_ID);

            // then
            assertThat(result.name()).isEqualTo("수정된 카페");
            then(cachePort).should().invalidateSearchCache(anyDouble(), anyDouble());
            then(cachePort).should().invalidateBusinessCache(BUSINESS_ID);
        }

        @Test
        @DisplayName("소유주 불일치 → BusinessOwnershipException (403)")
        void updateOwnerMismatch() {
            // given
            Business existing = createTestBusiness();
            given(businessWritePort.findByIdForWrite(BUSINESS_ID)).willReturn(Optional.of(existing));

            BusinessUpdateRequest request = new BusinessUpdateRequest(
                    "수정", null, null, null, null, null, null);

            // when & then
            assertThatThrownBy(() -> businessService.update(BUSINESS_ID, request, OTHER_OWNER_ID))
                    .isInstanceOf(BusinessOwnershipException.class);

            then(businessWritePort).should(never()).save(any());
        }

        @Test
        @DisplayName("존재하지 않는 ID → BusinessNotFoundException (404)")
        void updateNotFound() {
            // given
            given(businessWritePort.findByIdForWrite(BUSINESS_ID)).willReturn(Optional.empty());

            BusinessUpdateRequest request = new BusinessUpdateRequest(
                    "수정", null, null, null, null, null, null);

            // when & then
            assertThatThrownBy(() -> businessService.update(BUSINESS_ID, request, OWNER_ID))
                    .isInstanceOf(BusinessNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("delete")
    class Delete {

        @Test
        @DisplayName("정상 삭제 시 deleteById + 캐시 무효화")
        void deleteSuccess() {
            // given
            Business existing = createTestBusiness();
            given(businessWritePort.findByIdForWrite(BUSINESS_ID)).willReturn(Optional.of(existing));

            // when
            businessService.delete(BUSINESS_ID, OWNER_ID);

            // then
            then(businessWritePort).should().deleteById(BUSINESS_ID);
            then(cachePort).should().invalidateSearchCache(37.5665, 126.9780);
            then(cachePort).should().invalidateBusinessCache(BUSINESS_ID);
        }

        @Test
        @DisplayName("소유주 불일치 → BusinessOwnershipException (403)")
        void deleteOwnerMismatch() {
            // given
            Business existing = createTestBusiness();
            given(businessWritePort.findByIdForWrite(BUSINESS_ID)).willReturn(Optional.of(existing));

            // when & then
            assertThatThrownBy(() -> businessService.delete(BUSINESS_ID, OTHER_OWNER_ID))
                    .isInstanceOf(BusinessOwnershipException.class);

            then(businessWritePort).should(never()).deleteById(anyLong());
        }

        @Test
        @DisplayName("존재하지 않는 ID → BusinessNotFoundException (404)")
        void deleteNotFound() {
            // given
            given(businessWritePort.findByIdForWrite(BUSINESS_ID)).willReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> businessService.delete(BUSINESS_ID, OWNER_ID))
                    .isInstanceOf(BusinessNotFoundException.class);
        }
    }
}
