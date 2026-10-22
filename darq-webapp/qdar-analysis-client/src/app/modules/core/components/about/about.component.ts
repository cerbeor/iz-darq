import {Component, OnInit} from '@angular/core';
import {Observable} from 'rxjs';
import {IServerInfo, ServerInfoService} from '../../services/app-info.service';

export interface IReleaseNote {
  version: string;
  date: string;
  updates: string[];
}

@Component({
  selector: 'app-about',
  templateUrl: './about.component.html',
  styleUrls: ['./about.component.scss']
})
export class AboutComponent implements OnInit {

  info$: Observable<IServerInfo>;

  // Static for now, there is no release notes endpoint on the server yet.
  releaseNotes: IReleaseNote[] = [];

  constructor(private serverInfo: ServerInfoService) {
    this.info$ = this.serverInfo.getServerInfo();
  }

  ngOnInit(): void {
  }

}
