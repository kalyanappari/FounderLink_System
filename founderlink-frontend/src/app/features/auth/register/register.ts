import { Component, signal } from '@angular/core';
import { FormBuilder, FormGroup, Validators, ReactiveFormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { CommonModule } from '@angular/common';
import { AuthService } from '../../../core/services/auth.service';

@Component({
  selector: 'app-register',
  imports: [CommonModule, ReactiveFormsModule, RouterLink],
  templateUrl: './register.html',
  styleUrl: './register.css'
})
export class RegisterComponent {
  form: FormGroup;
  loading    = signal(false);
  errorMsg   = signal('');
  successMsg = signal('');
  showPassword = false;

  readonly roles = [
    { value: 'FOUNDER',   icon: '🚀', label: 'Founder',    desc: 'Build a startup' },
    { value: 'INVESTOR',  icon: '💼', label: 'Investor',   desc: 'Fund startups' },
    { value: 'COFOUNDER', icon: '🤝', label: 'Co-Founder', desc: 'Join a team' }
  ];

  readonly rolePreviews = [
    { role: 'Founder',    icon: '🚀', desc: 'Create and manage your startup' },
    { role: 'Investor',   icon: '💼', desc: 'Discover and fund opportunities' },
    { role: 'Co-Founder', icon: '🤝', desc: 'Join teams and build together' },
  ];

  readonly stats = [
    { value: '500+', label: 'Startups' },
    { value: '200+', label: 'Investors' },
    { value: '1K+',  label: 'Members' },
  ];

  get selectedRole(): string {
    const v = this.role.value as string;
    if (!v) return '';
    return v === 'FOUNDER' ? 'Founder' : v === 'INVESTOR' ? 'Investor' : 'Co-Founder';
  }

  constructor(
    private fb: FormBuilder,
    private authService: AuthService,
    private router: Router
  ) {
    this.form = this.fb.group({
      name:     ['', [Validators.required, Validators.minLength(2)]],
      email:    ['', [Validators.required, Validators.email]],
      password: ['', [Validators.required, Validators.minLength(6)]],
      role:     ['', Validators.required]
    });
  }

  get name()     { return this.form.get('name')!; }
  get email()    { return this.form.get('email')!; }
  get password() { return this.form.get('password')!; }
  get role()     { return this.form.get('role')!; }

  onSubmit(): void {
    if (this.form.invalid) { this.form.markAllAsTouched(); return; }
    this.loading.set(true);
    this.errorMsg.set('');

    this.authService.register(this.form.value).subscribe({
      next: () => {
        this.loading.set(false);
        this.successMsg.set('Account created successfully! Redirecting to login...');
        setTimeout(() => this.router.navigate(['/auth/login']), 1800);
      },
      error: (err) => {
        this.loading.set(false);
        this.errorMsg.set(err.error?.message || 'Registration failed. Please try again.');
      }
    });
  }
}
