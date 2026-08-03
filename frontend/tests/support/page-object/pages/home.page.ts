import { isMobileView } from '../../utils/viewUtils';
import { expect, Page } from '@playwright/test';

export default class HomePage {
    page: Page;

    constructor(page: Page) {
        this.page = page;
    }

    public async goto() {
        await this.page.goto('http://localhost:3000');
    }

    //Locators - non-authenticated user
    // Desktop renders Sign In as a plain <a href> (role "link"); the mobile drawer wraps
    // it in a Button too (role "button"). getByRole('button', ...) alone only matched the
    // mobile case once the desktop markup switched to an anchor, hanging every desktop spec
    // at login. Both nodes stay in the DOM regardless of viewport — the closed drawer is
    // moved off-canvas, not unmounted, and isn't reliably `:visible`-filterable across
    // browser engines — so this picks the role explicitly rather than matching either.
    loginLink = (mobile: boolean) =>
        mobile
            ? this.page.getByRole('button', { name: 'Sign In' })
            : this.page.getByRole('link', { name: 'Sign In' });
    signupLink = () => this.page.getByRole('button', { name: 'Register' });
    languageButton = () => this.page.getByRole('button', { name: 'Switch language' });
    homeLink = () => this.page.getByRole('link', { name: 'Home' });
    browseLink = () => this.page.getByRole('banner').getByRole('link', { name: 'Browse' });

    usernameTextbox = () => this.page.getByRole('textbox', { name: 'Username or Email address' });
    passwordTextbox = () => this.page.getByRole('textbox', { name: 'Password' });
    loginButton = () => this.page.getByRole('button', { name: 'Continue', exact: true });

    //Locators - authenticated user
    dashboardLink = () => this.page.getByRole('link', { name: 'Dashboard' });
    loginErrorMessage = () => this.page.getByText('Incorrect email address, username, or password');
    userDropdown = (username: string) => this.page.getByRole('button', { name: username })
    profileLink = () => this.page.getByRole('menuitem', { name: 'Profile' });
    logoutLink = () => this.page.getByRole('button', { name: 'Logout' });
    hamburgerMenuButton = () => this.page.getByRole('banner').getByRole('button');

    //Actions
    public async clickLoginLink() {
        const mobile = await isMobileView(this.page);

        if (mobile) {
            await this.hamburgerMenuButton().click();
        }
        await this.loginLink(mobile).click();
    }

    public async clickLogoutLink(username: string) {
        await this.userDropdown(username).click();
        await this.logoutLink().click();
    }

    public async clickDashboardLink() {
        const mobile = await isMobileView(this.page);

        if (mobile) {
            await this.hamburgerMenuButton().click();
        }
        await this.dashboardLink().click();
    }

    public async login(username: string, password: string) {
        await this.clickLoginLink();
        await this.usernameTextbox().fill(username);
        await this.passwordTextbox().fill(password);
        await this.loginButton().click();
    }

    public async assertUserLoggedIn(username: string) {
        const mobile = await isMobileView(this.page);

        if (mobile) {
            await this.hamburgerMenuButton().click();
        }
        await expect(this.userDropdown(username)).toBeVisible({ timeout: 15000 });
    }

    public async viewUserProfile(username: string) {
        await this.userDropdown(username).click();
        await this.profileLink().click();
    }

    public async setLanguageToEnglish() {
        // if the current url contains /fr, we are in french mode
        const currentUrl = this.page.url();
        if (currentUrl.includes('/fr')) {
            await Promise.all([
                this.page.waitForURL(url => !url.toString().includes('/fr')),
                this.languageButton().click(),
            ]);
        }

    }
}