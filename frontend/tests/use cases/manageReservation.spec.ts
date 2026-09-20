import { test, expect } from "../../fixtures/basePages";
import ReservationManagementPage from "../../support/page-object/pages/mediaOwner/reservationManagement.page";
import DashboardPage from "../../support/page-object/pages/dashboard.page";

test.describe("Reservation Management - Approve and Deny", () => {
    test.use({
        baseURL: "http://localhost:3000/en",
    });

    let reservationPage: ReservationManagementPage;
    let dashboardPage: DashboardPage;

    const testData = {
        mediaOwnerUser: {
            username: "mediaowner",
            password: "Password1!",
        },
        unauthorizedUser: {
            username: "advertiser",
            password: "Password1!",
        },
        pendingReservation: {
            campaignName: "Summer Campaign 2024",
            mediaId: "test-media-id-123",
            reservationId: "test-reservation-id-456",
        },
    };

    test.beforeEach(async ({ page }) => {
        reservationPage = new ReservationManagementPage(page);
        dashboardPage = new DashboardPage(page);
    });

    test("Approve Reservation - Happy Path", async ({ homePage, page }) => {
        await homePage.goto();
        await homePage.clickLoginLink();
        await homePage.usernameTextbox().fill(testData.mediaOwnerUser.username);
        await homePage.passwordTextbox().fill(testData.mediaOwnerUser.password);
        await homePage.loginButton().click();
        await homePage.assertUserLoggedIn(testData.mediaOwnerUser.username);

        await reservationPage.navigateToAdvertisements();

        await reservationPage.openReservationDetails(testData.pendingReservation.campaignName);
        await reservationPage.assertOnReservationDetailsPage();

        await reservationPage.assertReservationStatus("Pending");
        await reservationPage.assertApproveButtonVisible();

        await reservationPage.approveReservation();

        await reservationPage.assertSuccessNotification();

        await expect(page).toHaveURL(/advertisements/);
    });

    test("Deny Reservation - Happy Path", async ({ homePage, page }) => {
        await homePage.goto();
        await homePage.clickLoginLink();
        await homePage.usernameTextbox().fill(testData.mediaOwnerUser.username);
        await homePage.passwordTextbox().fill(testData.mediaOwnerUser.password);
        await homePage.loginButton().click();
        await homePage.assertUserLoggedIn(testData.mediaOwnerUser.username);

        await reservationPage.navigateToAdvertisements();

        await reservationPage.openReservationDetails(testData.pendingReservation.campaignName);
        await reservationPage.assertOnReservationDetailsPage();

        await reservationPage.assertReservationStatus("Pending");
        await reservationPage.assertDenyButtonVisible();

        await reservationPage.denyReservation(
            "Content Policy",
            "The advertisement content violates our community guidelines"
        );

        await reservationPage.assertSuccessNotification();

        await expect(page).toHaveURL(/advertisements/);
    });

    test("Deny Reservation - Without Description When Required", async ({ homePage }) => {
        await homePage.goto();
        await homePage.clickLoginLink();
        await homePage.usernameTextbox().fill(testData.mediaOwnerUser.username);
        await homePage.passwordTextbox().fill(testData.mediaOwnerUser.password);
        await homePage.loginButton().click();
        await homePage.assertUserLoggedIn(testData.mediaOwnerUser.username);

        await reservationPage.navigateToAdvertisements();

        await reservationPage.openReservationDetails(testData.pendingReservation.campaignName);
        await reservationPage.assertOnReservationDetailsPage();

        await reservationPage.denyReservationWithoutDescription("Other");

        await reservationPage.assertValidationError();

        await reservationPage.assertReservationStatus("Pending");
    });

    test("Approve Reservation - Forbidden (Unauthorized User)", async ({ homePage, page }) => {
        await homePage.goto();
        await homePage.clickLoginLink();
        await homePage.usernameTextbox().fill(testData.unauthorizedUser.username);
        await homePage.passwordTextbox().fill(testData.unauthorizedUser.password);
        await homePage.loginButton().click();
        await homePage.assertUserLoggedIn(testData.unauthorizedUser.username);

        const detailsUrl = `/dashboard/media-owner/advertisements/${testData.pendingReservation.reservationId}`;
        await page.goto(detailsUrl);

        await reservationPage.assertForbiddenError().catch(async () => {
            await expect(page).not.toHaveURL(new RegExp(testData.pendingReservation.reservationId));
        });
    });

    test("Deny Reservation - Forbidden (Unauthorized User)", async ({ homePage, page }) => {
        await homePage.goto();
        await homePage.clickLoginLink();
        await homePage.usernameTextbox().fill(testData.unauthorizedUser.username);
        await homePage.passwordTextbox().fill(testData.unauthorizedUser.password);
        await homePage.loginButton().click();
        await homePage.assertUserLoggedIn(testData.unauthorizedUser.username);

        const detailsUrl = `/dashboard/media-owner/advertisements/${testData.pendingReservation.reservationId}`;
        await page.goto(detailsUrl);

        await reservationPage.assertForbiddenError().catch(async () => {
            await expect(page).not.toHaveURL(new RegExp(testData.pendingReservation.reservationId));
        });
    });

    test("Approve Reservation - Already Approved", async ({ homePage }) => {
        await homePage.goto();
        await homePage.clickLoginLink();
        await homePage.usernameTextbox().fill(testData.mediaOwnerUser.username);
        await homePage.passwordTextbox().fill(testData.mediaOwnerUser.password);
        await homePage.loginButton().click();
        await homePage.assertUserLoggedIn(testData.mediaOwnerUser.username);

        await reservationPage.navigateToAdvertisements();
        await reservationPage.openReservationDetails(testData.pendingReservation.campaignName);

        await reservationPage.approveReservation();
        await reservationPage.assertSuccessNotification();

        await reservationPage.navigateToAdvertisements();
        await reservationPage.openReservationDetails(testData.pendingReservation.campaignName);

        await reservationPage.assertApproveButtonNotVisible();
        await reservationPage.assertReservationStatus("Approved");
    });

    test("Deny Reservation - Already Approved", async ({ homePage }) => {
        await homePage.goto();
        await homePage.clickLoginLink();
        await homePage.usernameTextbox().fill(testData.mediaOwnerUser.username);
        await homePage.passwordTextbox().fill(testData.mediaOwnerUser.password);
        await homePage.loginButton().click();
        await homePage.assertUserLoggedIn(testData.mediaOwnerUser.username);

        await reservationPage.navigateToAdvertisements();
        await reservationPage.openReservationDetails(testData.pendingReservation.campaignName);

        await reservationPage.approveReservation();
        await reservationPage.assertSuccessNotification();

        await reservationPage.navigateToAdvertisements();
        await reservationPage.openReservationDetails(testData.pendingReservation.campaignName);

        await reservationPage.assertDenyButtonNotVisible();
        await reservationPage.assertReservationStatus("Approved");
    });
});