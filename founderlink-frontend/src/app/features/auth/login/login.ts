import { Component, signal, OnInit } from '@angular/core';
import { FormBuilder, FormGroup, Validators, ReactiveFormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { CommonModule } from '@angular/common';
import { AuthService } from '../../../core/services/auth.service';
import { UserService } from '../../../core/services/user.service';

@Component({
  selector: 'app-login',
  imports: [CommonModule, ReactiveFormsModule, RouterLink],
  templateUrl: './login.html',
  styleUrl: './login.css'
})
export class LoginComponent implements OnInit {
  form: FormGroup;
  loading          = signal(false);
  googleLoading    = signal(false);
  errorMsg         = signal('');
  showVerifyBanner = signal(false);
  showPassword     = false;
  stats            = signal({ founders: 350, investors: 200, cofounders: 120 });

  readonly features = [
    { icon: 'founder',   title: 'Launch Your Startup',    desc: 'Create your profile and attract top talent and investors' },
    { icon: 'investor',  title: 'Connect with Investors',  desc: 'Get funded by verified investors who believe in your vision' },
    { icon: 'cofounder', title: 'Build Your Dream Team',   desc: 'Find co-founders and team members with the right skills' },
  ];

  constructor(
    private fb: FormBuilder,
    private authService: AuthService,
    private userService: UserService,
    private router: Router
  ) {
    this.form = this.fb.group({
      email:    ['', [Validators.required, Validators.email]],
      password: ['', Validators.required]
    });
  }

  ngOnInit(): void {
    this.userService.getPublicStats().subscribe({
      next: (data) => this.stats.set(data),
      error: () => console.warn('Failed to load public stats')
    });
  }

  get email()    { return this.form.get('email')!; }
  get password() { return this.form.get('password')!; }

  onSubmit(): void {
    if (this.form.invalid) { this.form.markAllAsTouched(); return; }
    this.loading.set(true);
    this.errorMsg.set('');
    this.showVerifyBanner.set(false);

    this.authService.login(this.form.value).subscribe({
      next: () => {
        this.loading.set(false);
        this.router.navigate(['/dashboard']);
      },
      error: (err: any) => {
        this.loading.set(false);
        const msg: string = err.error?.message || '';
        // Backend returns HTTP 403 for unverified emails — show inline verify banner
        if (err.status === 403 && msg.toLowerCase().includes('verify')) {
          this.showVerifyBanner.set(true);
          localStorage.setItem('pendingVerificationEmail', this.form.value.email);
        } else {
          this.errorMsg.set(msg || 'Invalid email or password. Please try again.');
        }
      }
    });
  }

  goToVerify(): void {
    this.router.navigate(['/auth/verify-email']);
  }

  /** Triggers Google One-Tap / popup via the Google Identity Services library */
  signInWithGoogle(): void {
    const google = (window as any).google;
    if (!google) {
      this.errorMsg.set('Google Sign-In is not available. Please try refreshing the page.');
      return;
    }
    this.googleLoading.set(true);
    this.errorMsg.set('');

    google.accounts.id.initialize({
      client_id: (window as any).__GOOGLE_CLIENT_ID__ || '',
      callback: (response: any) => this.handleGoogleCallback(response)
    });
    google.accounts.id.prompt();
  }

  private handleGoogleCallback(response: { credential: string }): void {
    this.authService.loginWithGoogle(response.credential).subscribe({
      next: (httpResponse: any) => {
        this.googleLoading.set(false);
        if (httpResponse.status === 202) {
          // New user — save pending data and redirect to role picker
          const body = httpResponse.body;
          sessionStorage.setItem('oauthToken', body.oauthToken);
          sessionStorage.setItem('oauthEmail', body.email);
          sessionStorage.setItem('oauthName',  body.name || '');
          this.router.navigate(['/auth/oauth-role']);
        } else {
          // Existing user — store session and go to dashboard
          this.authService.storeSessionFromResponse(httpResponse.body);
          this.router.navigate(['/dashboard']);
        }
      },
      error: (err: any) => {
        this.googleLoading.set(false);
        this.errorMsg.set(err.error?.message || 'Google Sign-In failed. Please try again.');
      }
    });
  }
}
