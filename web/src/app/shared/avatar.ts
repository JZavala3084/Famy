import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { MemberInfo } from '../core/family.service';

@Component({
  selector: 'app-avatar',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `{{ member().initial }}`,
  host: {
    '[style.background]': 'member().color',
    '[style.width.px]': 'size()',
    '[style.height.px]': 'size()',
    '[style.font-size.px]': 'size() * 0.42',
    '[attr.title]': 'member().name',
    '[attr.aria-label]': 'member().name',
    role: 'img',
  },
  styles: `
    :host {
      border-radius: 50%;
      color: #fff;
      font-weight: 700;
      display: inline-flex;
      align-items: center;
      justify-content: center;
      flex: none;
      line-height: 1;
    }
  `,
})
export class Avatar {
  readonly member = input.required<MemberInfo>();
  readonly size = input(28);
}
