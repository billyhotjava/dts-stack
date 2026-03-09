import { expect, type Locator, type Page } from '@playwright/test';

export class AnalyticsAiAssistantPage {
  readonly page: Page;
  readonly sidebar: Locator;
  readonly expandButton: Locator;
  readonly collapseButton: Locator;
  readonly chat: Locator;
  readonly input: Locator;
  readonly sendButton: Locator;
  readonly approvalCard: Locator;
  readonly approveButton: Locator;
  readonly cancelButton: Locator;

  constructor(page: Page) {
    this.page = page;
    this.sidebar = page.getByTestId('analytics-copilot-sidebar');
    this.expandButton = page.getByTestId('analytics-copilot-expand');
    this.collapseButton = page.getByTestId('analytics-copilot-collapse');
    this.chat = page.getByTestId('analytics-copilot-chat');
    this.input = page.getByTestId('analytics-copilot-input');
    this.sendButton = page.getByTestId('analytics-copilot-send');
    this.approvalCard = page.getByTestId('analytics-copilot-approval');
    this.approveButton = page.getByTestId('analytics-copilot-approve');
    this.cancelButton = page.getByTestId('analytics-copilot-cancel');
  }

  async expectReady(): Promise<void> {
    await expect(this.sidebar).toBeVisible();
    if (await this.expandButton.isVisible().catch(() => false)) {
      await this.expandButton.click();
    }
    await expect(this.chat).toBeVisible();
  }

  async sendPrompt(prompt: string): Promise<void> {
    await this.input.fill(prompt);
    await this.sendButton.click();
  }

  async expectAssistantMessage(text: string): Promise<void> {
    await expect(this.chat).toContainText(text);
  }

  async expectApproval(toolId: string): Promise<void> {
    await expect(this.approvalCard).toBeVisible();
    await expect(this.approvalCard).toContainText(toolId);
  }

  async approve(): Promise<void> {
    await this.approveButton.click();
  }

  async cancel(): Promise<void> {
    await this.cancelButton.click();
  }

  async expectApprovalClosed(): Promise<void> {
    await expect(this.approvalCard).toHaveCount(0);
  }
}
