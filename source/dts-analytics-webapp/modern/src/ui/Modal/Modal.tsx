import { HTMLAttributes, forwardRef, ReactNode, useEffect, useRef, useCallback, KeyboardEvent } from 'react';
import { createPortal } from 'react-dom';
import './Modal.css';

export type ModalSize = 'sm' | 'md' | 'lg' | 'xl' | 'full';

export interface ModalProps extends Omit<HTMLAttributes<HTMLDivElement>, 'title'> {
  isOpen: boolean;
  onClose: () => void;
  title?: ReactNode;
  description?: string;
  size?: ModalSize;
  closeOnOverlayClick?: boolean;
  closeOnEscape?: boolean;
  showCloseButton?: boolean;
  footer?: ReactNode;
  children?: ReactNode;
}

export const Modal = forwardRef<HTMLDivElement, ModalProps>(
  (
    {
      isOpen,
      onClose,
      title,
      description,
      size = 'md',
      closeOnOverlayClick = true,
      closeOnEscape = true,
      showCloseButton = true,
      footer,
      className = '',
      children,
      ...props
    },
    ref
  ) => {
    const modalRef = useRef<HTMLDivElement>(null);
    const previousActiveElement = useRef<Element | null>(null);
    const focusTimerRef = useRef<number | null>(null);

    // Handle escape key
    const handleKeyDown = useCallback(
      (event: globalThis.KeyboardEvent) => {
        if (closeOnEscape && event.key === 'Escape') {
          onClose();
        }
      },
      [closeOnEscape, onClose]
    );

    // Focus trap
    const handleTabKey = (event: KeyboardEvent<HTMLDivElement>) => {
      if (event.key !== 'Tab') return;

      const focusableElements = modalRef.current?.querySelectorAll(
        'button, [href], input, select, textarea, [tabindex]:not([tabindex="-1"])'
      );

      if (!focusableElements || focusableElements.length === 0) return;

      const firstElement = focusableElements[0] as HTMLElement;
      const lastElement = focusableElements[focusableElements.length - 1] as HTMLElement;

      if (event.shiftKey) {
        if (document.activeElement === firstElement) {
          event.preventDefault();
          lastElement.focus();
        }
      } else {
        if (document.activeElement === lastElement) {
          event.preventDefault();
          firstElement.focus();
        }
      }
    };

    useEffect(() => {
      if (isOpen) {
        previousActiveElement.current = document.activeElement;
        document.addEventListener('keydown', handleKeyDown);
        document.body.style.overflow = 'hidden';

        // Focus the modal
        focusTimerRef.current = window.setTimeout(() => {
          const focusableElement = modalRef.current?.querySelector(
            'button, [href], input, select, textarea, [tabindex]:not([tabindex="-1"])'
          ) as HTMLElement;
          focusableElement?.focus();
        }, 0);

        return () => {
          if (focusTimerRef.current != null) {
            window.clearTimeout(focusTimerRef.current);
            focusTimerRef.current = null;
          }
          document.removeEventListener('keydown', handleKeyDown);
          document.body.style.overflow = '';

          // Restore focus
          if (previousActiveElement.current instanceof HTMLElement) {
            previousActiveElement.current.focus();
          }
        };
      }
    }, [isOpen, handleKeyDown]);

    if (!isOpen) return null;

    const handleOverlayClick = (event: React.MouseEvent) => {
      if (closeOnOverlayClick && event.target === event.currentTarget) {
        onClose();
      }
    };

    const modalContent = (
      <div
        className="mb-modal__overlay"
        onClick={handleOverlayClick}
        role="presentation"
      >
        <div
          ref={modalRef}
          role="dialog"
          aria-modal="true"
          aria-labelledby={title ? 'modal-title' : undefined}
          aria-describedby={description ? 'modal-description' : undefined}
          className={`mb-modal mb-modal--${size} ${className}`}
          onKeyDown={handleTabKey}
          {...props}
        >
          {(title || showCloseButton) && (
            <div className="mb-modal__header">
              <div className="mb-modal__header-content">
                {title && (
                  <h2 id="modal-title" className="mb-modal__title">
                    {title}
                  </h2>
                )}
                {description && (
                  <p id="modal-description" className="mb-modal__description">
                    {description}
                  </p>
                )}
              </div>
              {showCloseButton && (
                <button
                  type="button"
                  className="mb-modal__close"
                  onClick={onClose}
                  aria-label="Close modal"
                >
                  <svg
                    width="20"
                    height="20"
                    viewBox="0 0 24 24"
                    fill="none"
                    stroke="currentColor"
                    strokeWidth="2"
                    strokeLinecap="round"
                    strokeLinejoin="round"
                  >
                    <path d="M18 6 6 18" />
                    <path d="m6 6 12 12" />
                  </svg>
                </button>
              )}
            </div>
          )}
          <div className="mb-modal__body">{children}</div>
          {footer && <div className="mb-modal__footer">{footer}</div>}
        </div>
      </div>
    );

    return createPortal(modalContent, document.body);
  }
);

Modal.displayName = 'Modal';

// Confirm Dialog
export interface ConfirmDialogProps {
  isOpen: boolean;
  onClose: () => void;
  onConfirm: () => void;
  title: string;
  message: ReactNode;
  confirmText?: string;
  cancelText?: string;
  variant?: 'default' | 'danger';
  loading?: boolean;
}

export function ConfirmDialog({
  isOpen,
  onClose,
  onConfirm,
  title,
  message,
  confirmText = 'Confirm',
  cancelText = 'Cancel',
  variant = 'default',
  loading = false,
}: ConfirmDialogProps) {
  return (
    <Modal
      isOpen={isOpen}
      onClose={onClose}
      title={title}
      size="sm"
      footer={
        <div className="mb-modal__footer-actions">
          <button
            type="button"
            className="mb-button mb-button--secondary mb-button--md"
            onClick={onClose}
            disabled={loading}
          >
            {cancelText}
          </button>
          <button
            type="button"
            className={`mb-button mb-button--${variant === 'danger' ? 'danger' : 'primary'} mb-button--md`}
            onClick={onConfirm}
            disabled={loading}
          >
            {loading ? 'Loading...' : confirmText}
          </button>
        </div>
      }
    >
      <p className="mb-modal__message">{message}</p>
    </Modal>
  );
}
