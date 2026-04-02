import { Component, signal } from '@angular/core';
import { FormBuilder, FormGroup, Validators, ReactiveFormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { CommonModule } from '@angular/common';
import { AuthService } from '../../../core/services/auth.service';

@Component({
  selector: 'app-login',
  imports: [CommonModule, ReactiveFormsModule, RouterLink],
  templateUrl: './login.html',
  styleUrl: './login.css'
})
export class LoginComponent {
  form: FormGroup;
  loading = signal(false);
  errorMsg = signal('');
  showPassword = false;

  readonly features = [
    { icon: '🚀', title: 'Launch Your Startup',   desc: 'Create your profile and attract top talent and investors' },
    { icon: '💼', title: 'Connect with Investors', desc: 'Get funded by verified investors who believe in your vision' },
    { icon: '🤝', title: 'Build Your Dream Team',  desc: 'Find co-founders and team members with the right skills' },
  ];

  readonly stats = [
    { value: '500+', label: 'Startups' },
    { value: '200+', label: 'Investors' },
    { value: '1K+',  label: 'Members' },
  ];

  constructor(
    private fb: FormBuilder,
    private authService: AuthService,
    private router: Router
  ) {
    this.form = this.fb.group({
      email:    ['', [Validators.required, Validators.email]],
      password: ['', Validators.required]
    });
  }

  get email()    { return this.form.get('email')!; }
  get password() { return this.form.get('password')!; }

  onSubmit(): void {
    if (this.form.invalid) { this.form.markAllAsTouched(); return; }
    this.loading.set(true);
    this.errorMsg.set('');

    this.authService.login(this.form.value).subscribe({
      next: () => {
        this.loading.set(false);
        this.router.navigate(['/dashboard']);
      },
      error: (err) => {
        this.loading.set(false);
        this.errorMsg.set(err.error?.message || 'Invalid email or password. Please try again.');
      }
    });
  }
}
