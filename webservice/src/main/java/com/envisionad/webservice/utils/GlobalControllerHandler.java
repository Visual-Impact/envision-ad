package com.envisionad.webservice.utils;

import com.envisionad.webservice.admin.exceptions.DuplicateAccountException;
import com.envisionad.webservice.advertisement.exceptions.*;
import com.envisionad.webservice.bundle.exceptions.BundleHasActiveSubscriptionsException;
import com.envisionad.webservice.bundle.exceptions.BundleNoEligibleMediaException;
import com.envisionad.webservice.bundle.exceptions.BundleNotActiveException;
import com.envisionad.webservice.bundle.exceptions.BundleNotFoundException;
import com.envisionad.webservice.bundle.exceptions.NotAdvertiserException;
import com.envisionad.webservice.business.exceptions.*;
import com.envisionad.webservice.payment.exceptions.BundleSubscriptionAlreadyPaidException;
import com.envisionad.webservice.payment.exceptions.BundleSubscriptionNotFoundException;
import com.envisionad.webservice.payment.exceptions.CouponNotFoundException;
import com.envisionad.webservice.payment.exceptions.DuplicateBundleSubscriptionException;
import com.envisionad.webservice.payment.exceptions.DuplicateCouponCodeException;
import com.envisionad.webservice.payment.exceptions.InvalidCouponException;
import com.envisionad.webservice.payment.exceptions.StripeAccountNotOnboardedException;
import com.envisionad.webservice.venue.exceptions.VenueNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import com.envisionad.webservice.media.exceptions.MediaNotFoundException;
import com.envisionad.webservice.media.exceptions.MediaRejectedReactivationException;
import com.envisionad.webservice.media.exceptions.InvalidMediaStatusTransitionException;
import com.envisionad.webservice.proofofdisplay.exceptions.AdvertiserEmailNotFoundException;
import com.envisionad.webservice.proofofdisplay.exceptions.MediaNotInActiveSubscriptionException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;
import static org.springframework.http.HttpStatus.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RestControllerAdvice
public class GlobalControllerHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalControllerHandler.class);

    @ResponseStatus(NOT_FOUND)
    @ExceptionHandler(BusinessNotFoundException.class)
    public HttpErrorInfo handleBusinessNotFoundException(BusinessNotFoundException ex) {
        return createHttpErrorInfo(NOT_FOUND, ex);
    }

    @ResponseStatus(BAD_REQUEST)
    @ExceptionHandler(DuplicateBusinessNameException.class)
    public HttpErrorInfo handleDuplicateBusinessNameException(DuplicateBusinessNameException ex) {
        return createHttpErrorInfo(BAD_REQUEST, ex);
    }

    @ResponseStatus(CONFLICT)
    @ExceptionHandler(DuplicateAccountException.class)
    public HttpErrorInfo handleDuplicateAccountException(DuplicateAccountException ex) {
        return createHttpErrorInfo(CONFLICT, ex);
    }

    @ResponseStatus(BAD_REQUEST)
    @ExceptionHandler(BadBusinessRequestException.class)
    public HttpErrorInfo handleBadBusinessException(BadBusinessRequestException ex) {
        return createHttpErrorInfo(BAD_REQUEST, ex);
    }

    @ResponseStatus(NOT_FOUND)
    @ExceptionHandler(BusinessEmployeeNotFoundException.class)
    public HttpErrorInfo handleBusinessEmployeeNotFoundException(BusinessEmployeeNotFoundException ex) {
        return createHttpErrorInfo(NOT_FOUND, ex);
    }

    @ResponseStatus(BAD_REQUEST)
    @ExceptionHandler(DuplicateBusinessEmployeeException.class)
    public HttpErrorInfo handleDuplicateBusinessEmployeeException(DuplicateBusinessEmployeeException ex) {
        return createHttpErrorInfo(BAD_REQUEST, ex);
    }

    @ResponseStatus(NOT_FOUND)
    @ExceptionHandler(InvitationNotFoundException.class)
    public HttpErrorInfo handleInvitationNotFoundException(InvitationNotFoundException ex) {
        return createHttpErrorInfo(NOT_FOUND, ex);
    }

    @ResponseStatus(BAD_REQUEST)
    @ExceptionHandler(BadInvitationRequestException.class)
    public HttpErrorInfo handleBadInvitationRequestException(BadInvitationRequestException ex) {
        return createHttpErrorInfo(BAD_REQUEST, ex);
    }

    @ResponseStatus(NOT_FOUND)
    @ExceptionHandler(VerificationNotFoundException.class)
    public HttpErrorInfo handleVerificationNotFoundException(VerificationNotFoundException ex) {
        return createHttpErrorInfo(NOT_FOUND, ex);
    }

    @ResponseStatus(BAD_REQUEST)
    @ExceptionHandler(BusinessAlreadyVerifiedException.class)
    public HttpErrorInfo handleBusinessAlreadyVerifiedException(BusinessAlreadyVerifiedException ex) {
        return createHttpErrorInfo(BAD_REQUEST, ex);
    }

    @ResponseStatus(BAD_REQUEST)
    @ExceptionHandler(BadVerificationRequestException.class)
    public HttpErrorInfo handleBadVerificationRequestException(BadVerificationRequestException ex) {
        return createHttpErrorInfo(BAD_REQUEST, ex);
    }

    @ResponseStatus(BAD_REQUEST)
    @ExceptionHandler(IllegalArgumentException.class)
    public HttpErrorInfo handleIllegalArgumentException(IllegalArgumentException ex) {
        return createHttpErrorInfo(BAD_REQUEST, ex);
    }

    @ResponseStatus(HttpStatus.UNPROCESSABLE_ENTITY)
    @ExceptionHandler(InvalidAdTypeException.class)
    public HttpErrorInfo handleInvalidAdType(InvalidAdTypeException ex) {
        return createHttpErrorInfo(HttpStatus.UNPROCESSABLE_ENTITY, ex);
    }

    @ResponseStatus(HttpStatus.UNPROCESSABLE_ENTITY)
    @ExceptionHandler(VideoTooLongException.class)
    public HttpErrorInfo handleVideoTooLong(VideoTooLongException ex) {
        return createHttpErrorInfo(HttpStatus.UNPROCESSABLE_ENTITY, ex);
    }

    @ResponseStatus(NOT_FOUND)
    @ExceptionHandler(AdCampaignNotFoundException.class)
    public HttpErrorInfo handleAdCampaignNotFoundException(AdCampaignNotFoundException ex) {
        return createHttpErrorInfo(NOT_FOUND, ex);
    }

    @ResponseStatus(NOT_FOUND)
    @ExceptionHandler(AdNotFoundException.class)
    public HttpErrorInfo handleAdNotFoundException(AdNotFoundException ex) {
        return createHttpErrorInfo(NOT_FOUND, ex);
    }

    @ResponseStatus(NOT_FOUND)
    @ExceptionHandler(MediaNotFoundException.class)
    public HttpErrorInfo handleMediaNotFoundException(MediaNotFoundException ex) {
        return createHttpErrorInfo(NOT_FOUND, ex);
    }

    @ResponseStatus(NOT_FOUND)
    @ExceptionHandler(AdvertiserEmailNotFoundException.class)
    public HttpErrorInfo handleAdvertiserEmailNotFoundException(AdvertiserEmailNotFoundException ex) {
        return createHttpErrorInfo(NOT_FOUND, ex);
    }

    @ResponseStatus(NOT_FOUND)
    @ExceptionHandler(VenueNotFoundException.class)
    public HttpErrorInfo handleVenueNotFoundException(VenueNotFoundException ex) {
        return createHttpErrorInfo(NOT_FOUND, ex);
    }

    @ResponseStatus(NOT_FOUND)
    @ExceptionHandler(BundleNotFoundException.class)
    public HttpErrorInfo handleBundleNotFoundException(BundleNotFoundException ex) {
        return createHttpErrorInfo(NOT_FOUND, ex);
    }

    @ResponseStatus(CONFLICT)
    @ExceptionHandler(BundleHasActiveSubscriptionsException.class)
    public HttpErrorInfo handleBundleHasActiveSubscriptionsException(
            BundleHasActiveSubscriptionsException ex) {
        return createHttpErrorInfo(CONFLICT, ex);
    }

    @ResponseStatus(CONFLICT)
    @ExceptionHandler(BundleNotActiveException.class)
    public HttpErrorInfo handleBundleNotActiveException(BundleNotActiveException ex) {
        return createHttpErrorInfo(CONFLICT, ex);
    }

    @ResponseStatus(CONFLICT)
    @ExceptionHandler(BundleNoEligibleMediaException.class)
    public HttpErrorInfo handleBundleNoEligibleMediaException(BundleNoEligibleMediaException ex) {
        return createHttpErrorInfo(CONFLICT, ex);
    }

    @ResponseStatus(CONFLICT)
    @ExceptionHandler(MediaRejectedReactivationException.class)
    public HttpErrorInfo handleMediaRejectedReactivationException(MediaRejectedReactivationException ex) {
        return createHttpErrorInfo(CONFLICT, ex);
    }

    @ResponseStatus(CONFLICT)
    @ExceptionHandler(InvalidMediaStatusTransitionException.class)
    public HttpErrorInfo handleInvalidMediaStatusTransitionException(InvalidMediaStatusTransitionException ex) {
        return createHttpErrorInfo(CONFLICT, ex);
    }

    @ResponseStatus(NOT_FOUND)
    @ExceptionHandler(BundleSubscriptionNotFoundException.class)
    public HttpErrorInfo handleBundleSubscriptionNotFoundException(
            BundleSubscriptionNotFoundException ex) {
        return createHttpErrorInfo(NOT_FOUND, ex);
    }

    @ResponseStatus(CONFLICT)
    @ExceptionHandler(BundleSubscriptionAlreadyPaidException.class)
    public HttpErrorInfo handleBundleSubscriptionAlreadyPaidException(
            BundleSubscriptionAlreadyPaidException ex) {
        return createHttpErrorInfo(CONFLICT, ex);
    }

    @ResponseStatus(CONFLICT)
    @ExceptionHandler(DuplicateBundleSubscriptionException.class)
    public HttpErrorInfo handleDuplicateBundleSubscriptionException(
            DuplicateBundleSubscriptionException ex) {
        return createHttpErrorInfo(CONFLICT, ex);
    }

    @ResponseStatus(CONFLICT)
    @ExceptionHandler(CampaignHasNoAdsException.class)
    public HttpErrorInfo handleCampaignHasNoAdsException(CampaignHasNoAdsException ex) {
        return createHttpErrorInfo(CONFLICT, ex);
    }

    @ResponseStatus(FORBIDDEN)
    @ExceptionHandler({SecurityException.class, AccessDeniedException.class})
    public HttpErrorInfo handleForbidden(Exception ex) {
        return createHttpErrorInfo(FORBIDDEN, ex);
    }

    @ResponseStatus(FORBIDDEN)
    @ExceptionHandler(NotAdvertiserException.class)
    public HttpErrorInfo handleNotAdvertiserException(NotAdvertiserException ex) {
        return new HttpErrorInfo(FORBIDDEN, ex.getMessage(), "NOT_ADVERTISER");
    }

    @ResponseStatus(FORBIDDEN)
    @ExceptionHandler(BusinessNotVerifiedException.class)
    public HttpErrorInfo handleBusinessNotVerifiedException(BusinessNotVerifiedException ex) {
        return new HttpErrorInfo(FORBIDDEN, ex.getMessage(), "BUSINESS_NOT_VERIFIED");
    }

    @ResponseStatus(NOT_FOUND)
    @ExceptionHandler(CouponNotFoundException.class)
    public HttpErrorInfo handleCouponNotFoundException(CouponNotFoundException ex) {
        return createHttpErrorInfo(NOT_FOUND, ex);
    }

    @ResponseStatus(BAD_REQUEST)
    @ExceptionHandler(DuplicateCouponCodeException.class)
    public HttpErrorInfo handleDuplicateCouponCodeException(DuplicateCouponCodeException ex) {
        return createHttpErrorInfo(BAD_REQUEST, ex);
    }

    @ResponseStatus(BAD_REQUEST)
    @ExceptionHandler(InvalidCouponException.class)
    public HttpErrorInfo handleInvalidCouponException(InvalidCouponException ex) {
        return new HttpErrorInfo(BAD_REQUEST, ex.getMessage(), ex.getCode());
    }

    @ResponseStatus(FORBIDDEN)
    @ExceptionHandler(StripeAccountNotOnboardedException.class)
    public HttpErrorInfo handleStripeAccountNotOnboardedException(StripeAccountNotOnboardedException ex) {
        return new HttpErrorInfo(FORBIDDEN, ex.getMessage(), "STRIPE_NOT_ONBOARDED");
    }

    @ResponseStatus(BAD_REQUEST)
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public HttpErrorInfo handleMethodArgumentNotValid(MethodArgumentNotValidException ex) {
        return createHttpErrorInfo(BAD_REQUEST, ex);
    }

    @ResponseStatus(INTERNAL_SERVER_ERROR)
    @ExceptionHandler(Exception.class)
    public HttpErrorInfo handleUnexpectedException(Exception ex) {
        log.error("Unhandled exception", ex);
        return createHttpErrorInfo(INTERNAL_SERVER_ERROR, ex);

    }

    @ResponseStatus(CONFLICT)
    @ExceptionHandler(CampaignIsTiedToSubscriptionException.class)
    public HttpErrorInfo handleCampaignIsTiedToSubscriptionException(CampaignIsTiedToSubscriptionException ex) {
        return createHttpErrorInfo(CONFLICT, ex);
    }

    @ResponseStatus(CONFLICT)
    @ExceptionHandler(MediaNotInActiveSubscriptionException.class)
    public HttpErrorInfo handleMediaNotInActiveSubscriptionException(MediaNotInActiveSubscriptionException ex) {
        return createHttpErrorInfo(CONFLICT, ex);
    }

    @ResponseStatus(CONFLICT)
    @ExceptionHandler(DataIntegrityViolationException.class)
    public HttpErrorInfo handleDataIntegrityViolationException(DataIntegrityViolationException ex) {
        log.warn("Data integrity violation", ex);
        return new HttpErrorInfo(CONFLICT, "The request could not be completed due to a conflicting database state.");
    }

    private HttpErrorInfo createHttpErrorInfo(HttpStatus httpStatus, Exception ex) {
        final String message = ex.getMessage();

        return new HttpErrorInfo(httpStatus, message);
    }
}